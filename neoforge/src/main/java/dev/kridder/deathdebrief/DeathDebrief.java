package dev.kridder.deathdebrief;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(DeathDebrief.MOD_ID)
public class DeathDebrief {
	public static final String MOD_ID = "deathdebrief";
	private static final int MAX_HITS = 10;

	private static final Map<UUID, ArrayDeque<HitRecord>> HITS = new HashMap<>();
	private static final Map<UUID, Integer> STACKS = new HashMap<>();
	private static final Map<UUID, Component> DEATH_MSGS = new HashMap<>();
	private static final Map<UUID, List<ItemStack>> ARMORS = new HashMap<>();
	private static final Map<UUID, Integer> XPS = new HashMap<>();

	private static final Map<UUID, Integer> LAST_STACKS = new HashMap<>();

	public DeathDebrief(IEventBus modBus) {
		modBus.addListener(DeathDebrief::registerPayloads);
		NeoForge.EVENT_BUS.register(DeathDebrief.class);
	}

	private static void registerPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToClient(DeathReportPayload.ID, DeathReportPayload.CODEC,
				(payload, ctx) -> DeathDebriefClient.onReport(payload));
	}

	private static int countStacks(ServerPlayer player) {
		Inventory inv = player.getInventory();
		int count = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (!inv.getItem(i).isEmpty()) {
				count++;
			}
		}
		return count;
	}

	private static String attackerType(DamageSource source) {
		Entity attacker = source.getEntity();
		return attacker != null ? EntityType.getKey(attacker.getType()).toString() : "";
	}

	private static String sourceText(DamageSource source) {
		return source.type().msgId();
	}

	private static String attackerName(DamageSource source) {
		Entity attacker = source.getEntity();
		return attacker != null ? attacker.getDisplayName().getString() : "";
	}

	private static void addHit(ArrayDeque<HitRecord> q, HitRecord h) {
		q.addLast(h);
		while (q.size() > MAX_HITS) {
			q.removeFirst();
		}
	}

	/** Records every hit (including the fatal one, with healthAfter <= 0). */
	@SubscribeEvent
	public static void onDamage(LivingDamageEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			DamageSource source = event.getSource();
			boolean blocked;
			try {
				blocked = player.isDamageSourceBlocked(source);
			} catch (Throwable t) {
				blocked = false;
			}
			ArrayDeque<HitRecord> q = HITS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
			addHit(q, new HitRecord(player.level().getGameTime(), sourceText(source), attackerName(source), attackerType(source),
					event.getNewDamage(), event.getOriginalDamage(), Math.max(0.0f, player.getHealth()), blocked));
		}
	}

	/** Snapshot taken before drops/inventory clearing. */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onDeathSnapshot(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			DamageSource source = event.getSource();
			int count = countStacks(player);
			if (count == 0) {
				count = LAST_STACKS.getOrDefault(player.getUUID(), 0);
			}
			STACKS.put(player.getUUID(), count);
			List<ItemStack> armorCopy = new ArrayList<>();
			armorCopy.add(player.getItemBySlot(EquipmentSlot.HEAD).copy());
			armorCopy.add(player.getItemBySlot(EquipmentSlot.CHEST).copy());
			armorCopy.add(player.getItemBySlot(EquipmentSlot.LEGS).copy());
			armorCopy.add(player.getItemBySlot(EquipmentSlot.FEET).copy());
			ARMORS.put(player.getUUID(), armorCopy);
			XPS.put(player.getUUID(), player.experienceLevel);
			ArrayDeque<HitRecord> q = HITS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
			HitRecord last = q.peekLast();
			if (last == null || last.healthAfter() > 0.0f) {
				float prev = last == null ? 0.0f : last.healthAfter();
				addHit(q, new HitRecord(player.level().getGameTime(), sourceText(source), attackerName(source),
						attackerType(source), prev, prev, 0.0f, false));
			}
			DEATH_MSGS.put(player.getUUID(), player.getCombatTracker().getDeathMessage());
		}
	}

	/** Death is final here (not cancelled by a totem etc.). */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			sendReport(player, event.getSource());
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (event.getServer().getTickCount() % 5 == 0) {
			for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
				if (p.isAlive()) {
					LAST_STACKS.put(p.getUUID(), countStacks(p));
				}
			}
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		UUID id = event.getEntity().getUUID();
		HITS.remove(id);
		STACKS.remove(id);
		LAST_STACKS.remove(id);
		DEATH_MSGS.remove(id);
		ARMORS.remove(id);
		XPS.remove(id);
	}

	private static void sendReport(ServerPlayer player, DamageSource source) {
		UUID id = player.getUUID();
		long now = player.level().getGameTime();
		ArrayDeque<HitRecord> q = HITS.remove(id);
		ArrayList<HitRecord> hits = q == null ? new ArrayList<>() : new ArrayList<>(q);
		if (hits.isEmpty() || hits.get(hits.size() - 1).healthAfter() > 0.0f) {
			float prev = hits.isEmpty() ? 0.0f : hits.get(hits.size() - 1).healthAfter();
			hits.add(new HitRecord(now, sourceText(source), attackerName(source), attackerType(source), prev, prev, 0.0f, false));
			while (hits.size() > MAX_HITS) {
				hits.remove(0);
			}
		}
		Component deathMsg = DEATH_MSGS.remove(id);
		if (deathMsg == null) {
			deathMsg = player.getCombatTracker().getDeathMessage();
		}
		List<ItemStack> armor = ARMORS.remove(id);
		if (armor == null) {
			armor = List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
		}
		int stacks = STACKS.containsKey(id) ? STACKS.remove(id) : LAST_STACKS.getOrDefault(id, 0);
		LAST_STACKS.remove(id);
		boolean keep = player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
		Entity attacker = source.getEntity();
		String killer = attacker != null ? attacker.getDisplayName().getString() : "";
		DeathReportPayload payload = new DeathReportPayload(
				deathMsg, killer,
				player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ(),
				player.level().dimension().location().toString(),
				(XPS.containsKey(id) ? XPS.remove(id) : player.experienceLevel), stacks, keep, now, hits, armor);
		PacketDistributor.sendToPlayer(player, payload);
	}
}
