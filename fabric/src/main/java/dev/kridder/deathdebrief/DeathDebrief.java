package dev.kridder.deathdebrief;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;

public class DeathDebrief implements ModInitializer {
	public static final String MOD_ID = "deathdebrief";
	private static final int MAX_HITS = 10;

	private static final Map<UUID, ArrayDeque<HitRecord>> HITS = new HashMap<>();
	private static final Map<UUID, Integer> STACKS = new HashMap<>();
	private static final Map<UUID, Text> DEATH_MSGS = new HashMap<>();
	private static final Map<UUID, List<ItemStack>> ARMORS = new HashMap<>();
	private static final Map<UUID, Integer> XPS = new HashMap<>();

	private static final Map<UUID, Integer> LAST_STACKS = new HashMap<>();

	private static int countStacks(ServerPlayerEntity player) {
		PlayerInventory inv = player.getInventory();
		int count = 0;
		for (int i = 0; i < inv.size(); i++) {
			if (!inv.getStack(i).isEmpty()) {
				count++;
			}
		}
		return count;
	}

	private static String attackerType(DamageSource source) {
		Entity attacker = source.getAttacker();
		return attacker != null ? EntityType.getId(attacker.getType()).toString() : "";
	}

	private static String sourceText(DamageSource source) {
		return source.getType().msgId();
	}

	private static String attackerName(DamageSource source) {
		Entity attacker = source.getAttacker();
		return attacker != null ? attacker.getDisplayName().getString() : "";
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(DeathReportPayload.ID, DeathReportPayload.CODEC);

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayerEntity player && player.getHealth() > 0.0f) {
				ArrayDeque<HitRecord> q = HITS.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
				q.addLast(new HitRecord(player.getWorld().getTime(), sourceText(source), attackerName(source), attackerType(source),
						damageTaken, baseDamageTaken, player.getHealth(), blocked));
				while (q.size() > MAX_HITS) {
					q.removeFirst();
				}
			}
		});

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity player) {
				int count = countStacks(player);
				if (count == 0) {
					count = LAST_STACKS.getOrDefault(player.getUuid(), 0);
				}
				STACKS.put(player.getUuid(), count);
				List<ItemStack> armorCopy = new ArrayList<>();
				armorCopy.add(player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).copy());
		armorCopy.add(player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).copy());
		armorCopy.add(player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS).copy());
		armorCopy.add(player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET).copy());
				ARMORS.put(player.getUuid(), armorCopy);
				XPS.put(player.getUuid(), player.experienceLevel);
				ArrayDeque<HitRecord> q = HITS.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
				HitRecord last = q.peekLast();
				if (last == null || last.healthAfter() > 0.0f) {
					q.addLast(new HitRecord(player.getWorld().getTime(), sourceText(source), attackerName(source), attackerType(source),
							amount, amount, 0.0f, false));
					while (q.size() > MAX_HITS) {
						q.removeFirst();
					}
				}
				DEATH_MSGS.put(player.getUuid(), player.getDamageTracker().getDeathMessage());
			}
			return true;
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player) {
				sendReport(player, source);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 5 == 0) {
				for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
					if (p.isAlive()) {
						LAST_STACKS.put(p.getUuid(), countStacks(p));
					}
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			HITS.remove(id);
			STACKS.remove(id);
			LAST_STACKS.remove(id);
			DEATH_MSGS.remove(id);
			ARMORS.remove(id);
			XPS.remove(id);
		});
	}

	private static void sendReport(ServerPlayerEntity player, DamageSource source) {
		UUID id = player.getUuid();
		long now = player.getWorld().getTime();
		ArrayDeque<HitRecord> q = HITS.remove(id);
		ArrayList<HitRecord> hits = q == null ? new ArrayList<>() : new ArrayList<>(q);
		// Fallback only if ALLOW_DEATH did not record the fatal hit (e.g. /kill).
		if (hits.isEmpty() || hits.get(hits.size() - 1).healthAfter() > 0.0f) {
			float prev = hits.isEmpty() ? 0.0f : hits.get(hits.size() - 1).healthAfter();
			hits.add(new HitRecord(now, sourceText(source), attackerName(source), attackerType(source), prev, prev, 0.0f, false));
			while (hits.size() > MAX_HITS) {
				hits.remove(0);
			}
		}
		Text deathMsg = DEATH_MSGS.remove(id);
		if (deathMsg == null) {
			deathMsg = player.getDamageTracker().getDeathMessage();
		}
		List<ItemStack> armor = ARMORS.remove(id);
		if (armor == null) {
			armor = List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
		}
		int stacks = STACKS.containsKey(id) ? STACKS.remove(id) : LAST_STACKS.getOrDefault(id, 0);
		LAST_STACKS.remove(id);
		boolean keep = player.getServerWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY);
		Entity attacker = source.getAttacker();
		String killer = attacker != null ? attacker.getDisplayName().getString() : "";
		DeathReportPayload payload = new DeathReportPayload(
				deathMsg, killer,
				player.getBlockPos().getX(), player.getBlockPos().getY(), player.getBlockPos().getZ(),
				player.getWorld().getRegistryKey().getValue().toString(),
				(XPS.containsKey(id) ? XPS.remove(id) : player.experienceLevel), stacks, keep, now, hits, armor);
		ServerPlayNetworking.send(player, payload);
	}
}
