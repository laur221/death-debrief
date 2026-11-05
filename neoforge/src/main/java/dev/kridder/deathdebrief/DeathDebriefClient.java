package dev.kridder.deathdebrief;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = DeathDebrief.MOD_ID, dist = Dist.CLIENT)
public class DeathDebriefClient {
	public static DeathReportPayload report;
	public static long receivedAt;

	private static KeyMapping openKey;
	private static Button deathBtn;
	private static long deathBtnOpenedAt;

	public DeathDebriefClient(IEventBus modBus) {
		ClientConfig.load();
		DeathWaypoint.init();
		modBus.addListener(DeathDebriefClient::registerKeys);
		modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) ->
				e.registerAboveAll(DeathWaypoint.HUD_LAYER, DeathWaypoint::renderHud));
		NeoForge.EVENT_BUS.register(DeathDebriefClient.class);
	}

	/** Called on the client main thread when a death report packet arrives. */
	public static void onReport(DeathReportPayload payload) {
		report = payload;
		receivedAt = System.currentTimeMillis();
		DeathRecord rec = DeathRecord.from(payload, receivedAt);
		DeathHistory.add(rec);
		try {
			WaypointManager.onDeath(rec);
		} catch (Throwable t) {
			System.err.println("[DeathDebrief] auto waypoint failed: " + t);
		}
	}

	private static void registerKeys(RegisterKeyMappingsEvent event) {
		openKey = new KeyMapping("key.deathdebrief.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B,
				"key.categories.deathdebrief");
		event.register(openKey);
	}

	@SubscribeEvent
	public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
		ClientConfig.load();
		DeathHistory.load(Minecraft.getInstance());
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		while (openKey != null && openKey.consumeClick()) {
			open(client);
		}
		if (deathBtn != null) {
			if (!(client.screen instanceof DeathScreen)) {
				deathBtn = null;
			} else if (!deathBtn.active && report != null && receivedAt >= deathBtnOpenedAt - 5000) {
				deathBtn.active = true;
			}
		}
	}

	@SubscribeEvent
	public static void onCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("deathdebrief")
				.executes(ctx -> {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> open(mc));
					return 1;
				})
				.then(Commands.literal("waypoint").then(Commands.literal("clear").executes(ctx -> {
					DeathWaypoint.clear();
					ctx.getSource().sendSuccess(() -> Component.translatable("deathdebrief.wp.cleared"), false);
					return 1;
				})))
				.then(Commands.literal("history").executes(ctx -> {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> openHistory(mc));
					return 1;
				})));
	}

	@SubscribeEvent
	public static void onScreenInit(ScreenEvent.Init.Post event) {
		if (event.getScreen() instanceof DeathScreen screen) {
			final long openedAt = System.currentTimeMillis();
			int w = screen.width;
			int h = screen.height;
			Button btn = Button.builder(Component.translatable("deathdebrief.button"),
					b -> Minecraft.getInstance().setScreen(new DebriefScreen(screen, 0)))
					.bounds(w / 2 - 100, h / 4 + 120, 200, 20).build();
			btn.active = report != null && receivedAt >= openedAt - 5000;
			event.addListener(btn);
			deathBtn = btn;
			deathBtnOpenedAt = openedAt;
		}
	}

	private static void open(Minecraft client) {
		if (DeathHistory.size() > 0) {
			client.setScreen(new DebriefScreen(client.screen, 0));
		} else if (client.player != null) {
			client.player.displayClientMessage(Component.translatable("deathdebrief.no_report"), true);
		}
	}

	private static void openHistory(Minecraft client) {
		if (DeathHistory.size() > 0) {
			client.setScreen(new HistoryScreen(client.screen));
		} else if (client.player != null) {
			client.player.displayClientMessage(Component.translatable("deathdebrief.no_report"), true);
		}
	}
}
