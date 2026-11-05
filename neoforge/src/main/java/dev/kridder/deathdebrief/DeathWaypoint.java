package dev.kridder.deathdebrief;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Built-in client-side death marker: world beam + HUD distance/arrow. */
public final class DeathWaypoint {
	private static final long DESPAWN_MS = 300_000L;
	private static final String[] ARROWS = { "↑", "↗", "→", "↘", "↓", "↙", "←", "↖" };
	private static final String SKULL = "☠";

	public static final ResourceLocation HUD_LAYER = ResourceLocation.fromNamespaceAndPath(DeathDebrief.MOD_ID, "waypoint_hud");

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

	private static String currentDimension(Minecraft mc) {
		return mc.level == null ? "" : mc.level.dimension().location().toString();
	}

	public static void init() {
		NeoForge.EVENT_BUS.register(DeathWaypoint.class);
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		clear("disconnect");
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		tick(Minecraft.getInstance());
	}

	private static void tick(Minecraft mc) {
		if (!active) {
			return;
		}
		if (System.currentTimeMillis() - createdAt > DESPAWN_MS) {
			clear("despawn timeout");
			return;
		}
		if (mc.player == null || mc.level == null || !dimension.equals(currentDimension(mc))) {
			return;
		}
		if (mc.player.isDeadOrDying() || mc.player.getHealth() <= 0.0f || mc.screen instanceof DeathScreen) {
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

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
			renderWorld(event);
		}
	}

	private static void renderWorld(RenderLevelStageEvent ev) {
		Minecraft mc = Minecraft.getInstance();
		if (!active || mc.level == null || mc.player == null || !dimension.equals(currentDimension(mc))) {
			return;
		}
		Vec3 cam = ev.getCamera().getPosition();
		Matrix4f m = new Matrix4f(ev.getPoseStack().last().pose());
		float hw = 0.125f;
		float cx = (float) (x + 0.5 - cam.x);
		float cz = (float) (z + 0.5 - cam.z);
		float y0 = (float) (y - cam.y);
		float y1 = y0 + 64.0f;
		float x0 = cx - hw;
		float x1 = cx + hw;
		float z0 = cz - hw;
		float z1 = cz + hw;

		MultiBufferSource.BufferSource vcp = mc.renderBuffers().bufferSource();
		VertexConsumer vc = vcp.getBuffer(RenderType.debugQuads());
		int r = 0xFF;
		int g = 0x30;
		int b = 0x30;
		int a0 = 90;
		int a1 = 0;
		quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a0, a1);
		quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a0, a1);
		quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a0, a1);
		quad(vc, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, a0, a1);
		vcp.endBatch(RenderType.debugQuads());

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
		lm.rotate(ev.getCamera().rotation());
		lm.scale(scale, -scale, scale);
		Font tr = mc.font;
		Component label = Component.literal(SKULL + " Death " + dist + " m");
		float tx = -tr.width(label) / 2.0f;
		tr.drawInBatch(label, tx, 0.0f, 0xFFFF5555, true, lm, vcp, Font.DisplayMode.SEE_THROUGH, 0x40000000, 0xF000F0);
		vcp.endBatch();
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, int r, int g, int b, int a0, int a1) {
		vc.addVertex(m, ax, ay, az).setColor(r, g, b, a0);
		vc.addVertex(m, bx, by, bz).setColor(r, g, b, a0);
		vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a1);
		vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a1);
	}

	private static boolean hudLogged;

	/** Registered as a GUI layer by the client mod class (see DeathDebriefClient). */
	public static void renderHud(GuiGraphics ctx, DeltaTracker tickCounter) {
		Minecraft mc = Minecraft.getInstance();
		if (!active || mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null
				|| !dimension.equals(currentDimension(mc))) {
			return;
		}
		double dx = x + 0.5 - mc.player.getX();
		double dy = y - mc.player.getY();
		double dz = z + 0.5 - mc.player.getZ();
		int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
		int idx = (int) Math.floorMod(Math.round(rel / 45.0), 8L);
		String s = SKULL + " " + dist + " m " + ARROWS[idx];
		if (!hudLogged) {
			hudLogged = true;
			System.out.println("[DeathDebrief] HUD indicator drawing: " + s);
		}
		ctx.drawCenteredString(mc.font, s, ctx.guiWidth() / 2, 4, 0xFFFF5555);
	}
}
