package dev.kridder.deathdebrief;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

public record DeathReportPayload(Text deathMessage, String killer, int x, int y, int z, String dimension,
		int xpLevel, int stacks, boolean keepInventory, long worldTime, List<HitRecord> hits, List<ItemStack> armor) implements CustomPayload {

	public static final Id<DeathReportPayload> ID = new Id<>(Identifier.of(DeathDebrief.MOD_ID, "death_report"));

	public static final PacketCodec<RegistryByteBuf, DeathReportPayload> CODEC = PacketCodec.of(
			(DeathReportPayload p, RegistryByteBuf buf) -> {
				TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, p.deathMessage);
				buf.writeString(p.killer);
				buf.writeInt(p.x);
				buf.writeInt(p.y);
				buf.writeInt(p.z);
				buf.writeString(p.dimension);
				buf.writeInt(p.xpLevel);
				buf.writeInt(p.stacks);
				buf.writeBoolean(p.keepInventory);
				buf.writeLong(p.worldTime);
				buf.writeVarInt(p.hits.size());
				for (HitRecord h : p.hits) {
					HitRecord.write(buf, h);
				}
				for (int i = 0; i < 4; i++) {
					ItemStack s = (p.armor != null && i < p.armor.size()) ? p.armor.get(i) : ItemStack.EMPTY;
					ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, s);
				}
			},
			(RegistryByteBuf buf) -> {
				Text msg = TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
				String killer = buf.readString();
				int x = buf.readInt();
				int y = buf.readInt();
				int z = buf.readInt();
				String dim = buf.readString();
				int xp = buf.readInt();
				int stacks = buf.readInt();
				boolean keep = buf.readBoolean();
				long time = buf.readLong();
				int n = buf.readVarInt();
				List<HitRecord> hits = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					hits.add(HitRecord.read(buf));
				}
				List<ItemStack> armor = new ArrayList<>();
				for (int i = 0; i < 4; i++) {
					armor.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
				}
				return new DeathReportPayload(msg, killer, x, y, z, dim, xp, stacks, keep, time, hits, armor);
			});

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
