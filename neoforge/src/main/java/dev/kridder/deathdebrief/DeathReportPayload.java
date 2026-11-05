package dev.kridder.deathdebrief;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record DeathReportPayload(Component deathMessage, String killer, int x, int y, int z, String dimension,
		int xpLevel, int stacks, boolean keepInventory, long worldTime, List<HitRecord> hits, List<ItemStack> armor) implements CustomPacketPayload {

	public static final Type<DeathReportPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(DeathDebrief.MOD_ID, "death_report"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DeathReportPayload> CODEC = StreamCodec.of(
			(RegistryFriendlyByteBuf buf, DeathReportPayload p) -> {
				ComponentSerialization.STREAM_CODEC.encode(buf, p.deathMessage);
				buf.writeUtf(p.killer);
				buf.writeInt(p.x);
				buf.writeInt(p.y);
				buf.writeInt(p.z);
				buf.writeUtf(p.dimension);
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
					ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
				}
			},
			(RegistryFriendlyByteBuf buf) -> {
				Component msg = ComponentSerialization.STREAM_CODEC.decode(buf);
				String killer = buf.readUtf();
				int x = buf.readInt();
				int y = buf.readInt();
				int z = buf.readInt();
				String dim = buf.readUtf();
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
					armor.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
				}
				return new DeathReportPayload(msg, killer, x, y, z, dim, xp, stacks, keep, time, hits, armor);
			});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
