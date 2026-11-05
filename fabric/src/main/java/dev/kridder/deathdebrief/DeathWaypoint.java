package dev.kridder.deathdebrief;

import org.joml.Matrix4f;


import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Built-in client-side death marker: world beam + HUD distance/arrow. */
public final class DeathWaypoint {
	private static final long DESPAWN_MS = 300_000L;
	private static final String[] ARROWS = { "↑", "↗", "→", "↘", "↓", "↙", "←", "↖" };

	public static double x;
	public static double y;
	public static double z;
	public static String dimension = "";
	public static long createdAt;
	private static boolean active;
	private static boolean armed;
	private static long setAt;

	private DeathWaypoint() {
	}

	public static boolean isActive() {
		return active;
	}

	public static boolean isAt(int bx, int by, int bz, String dim) {
		return active && (int) x == bx && (int) y == by && (int) z == bz && dimension.equals(dim);
	}

	public static void set(int bx, int by, int bz, String dim, long created) {
		x = bx;
		y = by;
		z = bz;
		dimension = dim == null ? "" : dim;
		createdAt = created;
		setAt = System.currentTimeMillis();
		armed = false;
		active = true;
	}

	/** Explicit clear (command). */
	public static void clear() {
		clear("explicit clear");
	}

	public static void clear(String reason) {
		if (active) {
			System.out.println("[DeathDebrief] Waypoint cleared: " + reason);
		}
		active = false;
	}

	private static String currentDimension(MinecraftClient mc) {
		return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(DeathWaypoint::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear("disconnect"));
		WorldRenderEvents.AFTER_TRANSLUCENT.register(DeathWaypoint::renderWorld);
		HudRenderCallback.EVENT.register(DeathWaypoint::renderHud);
	}

	private static void tick(MinecraftClient mc) {
		if (!active) {
			return;
		}
		if (System.currentTimeMillis() - createdAt > DESPAWN_MS) {
			clear("despawn timeout");
			return;
		}
		if (mc.player == null || mc.world == null || !dimension.equals(currentDimension(mc))) {
			return;
		}
		if (mc.player.isDead() || mc.player.getHealth() <= 0.0f || mc.currentScreen instanceof DeathScreen) {
			return;
		}
		double dx = mc.player.getX() - (x + 0.5);
		double dy = mc.player.getY() - y;
		double dz = mc.player.getZ() - (z + 0.5);
		double d2 = dx * dx + dy * dy + dz * dz;
		if (!armed) {
			if (d2 >= 100.0) {
				armed = true;
			}
			return;
		}
		if (System.currentTimeMillis() - setAt >= 5000L && d2 < 9.0) {
			clear("player within 3 blocks");
		}
	}

	private static void renderWorld(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!active || mc.world == null || !dimension.equals(currentDimension(mc)) || ctx.consumers() == null) {
			return;
		}
		Vec3d cam = ctx.camera().getPos();
		Matrix4f m = new Matrix4f(ctx.matrixStack().peek().getPositionMatrix());
		float hw = 0.125f;
		float cx = (float) (x + 0.5 - cam.x);
		float cz = (float) (z + 0.5 - cam.z);
		float y0 = (float) (y - cam.y);
		float y1 = y0 + 64.0f;
		float x0 = cx - hw;
		float x1 = cx + hw;
		float z0 = cz - hw;
		float z1 = cz + hw;

		VertexConsumerProvider vcp = ctx.consumers();
		VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());
		int r = 0xFF;
		int g = 0x30;
		int b = 0x30;
		int a0 = 90;
		int a1 = 0;
		quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a0, a1);
		quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a0, a1);
		quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a0, a1);
		quad(vc, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, a0, a1);
		if (vcp instanceof VertexConsumerProvider.Immediate imm) {
			imm.draw(RenderLayer.getDebugQuads());
		}

		// Billboarded floating label above the death block, visible through terrain.
		double ddx = x + 0.5 - cam.x;
		double ddy = y + 2.2 - cam.y;
		double ddz = z + 0.5 - cam.z;
		double pdx = x + 0.5 - mc.player.getX();
		double pdy = y - mc.player.getY();
		double pdz = z + 0.5 - mc.player.getZ();
		int dist = (int) Math.round(Math.sqrt(pdx * pdx + pdy * pdy + pdz * pdz));
		float scale = 0.025f * Math.max(1.0f, (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) / 8.0f);
		Matrix4f lm = new Matrix4f(m);
		lm.translate((float) ddx, (float) ddy, (float) ddz);
		lm.rotate(ctx.camera().getRotation());
		lm.scale(scale, -scale, scale);
		TextRenderer tr = mc.textRenderer;
		Text label = Text.literal("☠ Death " + dist + " m");
		float tx = -tr.getWidth(label) / 2.0f;
		VertexConsumerProvider.Immediate textBuf = mc.getBufferBuilders().getEntityVertexConsumers();
		tr.draw(label, tx, 0.0f, 0xFFFF5555, true, lm, textBuf, TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, 0xF000F0);
		textBuf.draw();
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, int r, int g, int b, int a0, int a1) {
		vc.vertex(m, ax, ay, az).color(r, g, b, a0);
		vc.vertex(m, bx, by, bz).color(r, g, b, a0);
		vc.vertex(m, cx, cy, cz).color(r, g, b, a1);
		vc.vertex(m, dx, dy, dz).color(r, g, b, a1);
	}

	private static boolean hudLogged;

	private static void renderHud(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!active || mc.player == null || mc.world == null || mc.options.hudHidden || mc.currentScreen != null
				|| !dimension.equals(currentDimension(mc))) {
			return;
		}
		double dx = x + 0.5 - mc.player.getX();
		double dy = y - mc.player.getY();
		double dz = z + 0.5 - mc.player.getZ();
		int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
		int idx = (int) Math.floorMod(Math.round(rel / 45.0), 8L);
		String s = "☠ " + dist + " m " + ARROWS[idx];
		if (!hudLogged) {
			hudLogged = true;
			System.out.println("[DeathDebrief] HUD indicator drawing: " + s);
		}
		ctx.drawCenteredTextWithShadow(mc.textRenderer, s, ctx.getScaledWindowWidth() / 2, 4, 0xFFFF5555);
	}
}