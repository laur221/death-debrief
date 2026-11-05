package dev.kridder.deathdebrief;

import net.minecraft.network.PacketByteBuf;

public record HitRecord(long tick, String source, String attacker, String attackerType, float taken, float beforeArmor, float healthAfter, boolean blocked) {
	public static void write(PacketByteBuf buf, HitRecord h) {
		buf.writeLong(h.tick);
		buf.writeString(h.source);
		buf.writeString(h.attacker);
		buf.writeString(h.attackerType);
		buf.writeFloat(h.taken);
		buf.writeFloat(h.beforeArmor);
		buf.writeFloat(h.healthAfter);
		buf.writeBoolean(h.blocked);
	}

	public static HitRecord read(PacketByteBuf buf) {
		return new HitRecord(buf.readLong(), buf.readString(), buf.readString(), buf.readString(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
	}
}
