package dev.kridder.deathdebrief;

import net.minecraft.network.FriendlyByteBuf;

public record HitRecord(long tick, String source, String attacker, String attackerType, float taken, float beforeArmor, float healthAfter, boolean blocked) {
	public static void write(FriendlyByteBuf buf, HitRecord h) {
		buf.writeLong(h.tick);
		buf.writeUtf(h.source);
		buf.writeUtf(h.attacker);
		buf.writeUtf(h.attackerType);
		buf.writeFloat(h.taken);
		buf.writeFloat(h.beforeArmor);
		buf.writeFloat(h.healthAfter);
		buf.writeBoolean(h.blocked);
	}

	public static HitRecord read(FriendlyByteBuf buf) {
		return new HitRecord(buf.readLong(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
	}
}
