package dev.kridder.deathdebrief;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Gson-serializable snapshot of one death report, stored in the client history. */
public class DeathRecord {
	public static class Hit {
		public long tick;
		public String source = "";
		public String attacker = "";
		public String attackerType = "";
		public float taken;
		public float beforeArmor;
		public float healthAfter;
		public boolean blocked;
	}

	public String message = "";
	public String killer = "";
	public int x;
	public int y;
	public int z;
	public String dimension = "";
	public int xpLevel;
	public int stacks;
	public boolean keepInventory;
	public long worldTime;
	public long receivedAt;
	public List<Hit> hitList = new ArrayList<>();
	/** Four entries "namespace:item|count", empty string for an empty slot. */
	public List<String> armorList = new ArrayList<>();

	private transient List<HitRecord> hitsCache;
	private transient List<ItemStack> armorCache;

	public static DeathRecord from(DeathReportPayload p, long receivedAt) {
		DeathRecord r = new DeathRecord();
		r.message = p.deathMessage().getString();
		r.killer = p.killer();
		r.x = p.x();
		r.y = p.y();
		r.z = p.z();
		r.dimension = p.dimension();
		r.xpLevel = p.xpLevel();
		r.stacks = p.stacks();
		r.keepInventory = p.keepInventory();
		r.worldTime = p.worldTime();
		r.receivedAt = receivedAt;
		for (HitRecord h : p.hits()) {
			Hit o = new Hit();
			o.tick = h.tick();
			o.source = h.source();
			o.attacker = h.attacker();
			o.attackerType = h.attackerType();
			o.taken = h.taken();
			o.beforeArmor = h.beforeArmor();
			o.healthAfter = h.healthAfter();
			o.blocked = h.blocked();
			r.hitList.add(o);
		}
		for (int i = 0; i < 4; i++) {
			ItemStack s = (p.armor() != null && i < p.armor().size()) ? p.armor().get(i) : ItemStack.EMPTY;
			r.armorList.add(s.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(s.getItem()) + "|" + s.getCount());
		}
		return r;
	}

	public Component deathMessage() {
		return Component.literal(message == null ? "" : message);
	}

	public String killer() {
		return killer == null ? "" : killer;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int z() {
		return z;
	}

	public String dimension() {
		return dimension == null ? "" : dimension;
	}

	public int xpLevel() {
		return xpLevel;
	}

	public int stacks() {
		return stacks;
	}

	public boolean keepInventory() {
		return keepInventory;
	}

	public long worldTime() {
		return worldTime;
	}

	public long receivedAt() {
		return receivedAt;
	}

	public List<HitRecord> hits() {
		if (hitsCache == null) {
			List<HitRecord> l = new ArrayList<>();
			if (hitList != null) {
				for (Hit h : hitList) {
					l.add(new HitRecord(h.tick, nz(h.source), nz(h.attacker), nz(h.attackerType), h.taken, h.beforeArmor,
							h.healthAfter, h.blocked));
				}
			}
			hitsCache = l;
		}
		return hitsCache;
	}

	public List<ItemStack> armor() {
		if (armorCache == null) {
			List<ItemStack> l = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				ItemStack st = ItemStack.EMPTY;
				if (armorList != null && i < armorList.size() && armorList.get(i) != null && !armorList.get(i).isEmpty()) {
					try {
						String[] parts = armorList.get(i).split("\\|");
						ResourceLocation id = ResourceLocation.tryParse(parts[0]);
						if (id != null) {
							Item item = BuiltInRegistries.ITEM.get(id);
							int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
							st = new ItemStack(item, Math.max(1, count));
						}
					} catch (Exception e) {
						st = ItemStack.EMPTY;
					}
				}
				l.add(st);
			}
			armorCache = l;
		}
		return armorCache;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
