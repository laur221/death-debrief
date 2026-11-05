package dev.kridder.deathdebrief;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

public class DeathDebriefClient implements ClientModInitializer {
	public static DeathReportPayload report;
	public static long receivedAt;

	private static KeyBinding openKey;

	@Override
	public void onInitializeClient() {
		SelfTest.init();
		ClientConfig.load();
		DeathWaypoint.init();
		ClientPlayNetworking.registerGlobalReceiver(DeathReportPayload.ID, (payload, context) -> {
			report = payload;
			receivedAt = System.currentTimeMillis();
			DeathRecord rec = DeathRecord.from(payload, receivedAt);
			DeathHistory.add(rec);
			try {
				WaypointManager.onDeath(rec);
			} catch (Throwable t) {
				System.err.println("[DeathDebrief] auto waypoint failed: " + t);
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ClientConfig.load();
			DeathHistory.load(client);
		});

		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.deathdebrief.open", GLFW.GLFW_KEY_B, "key.categories.deathdebrief"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.wasPressed()) {
				open(client);
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("deathdebrief")
						.executes(ctx -> {
							MinecraftClient mc = MinecraftClient.getInstance();
							mc.send(() -> open(mc));
							return 1;
						})
						.then(ClientCommandManager.literal("waypoint").then(ClientCommandManager.literal("clear").executes(ctx -> {
						DeathWaypoint.clear();
						ctx.getSource().sendFeedback(Text.translatable("deathdebrief.wp.cleared"));
						return 1;
					})))
					.then(ClientCommandManager.literal("history").executes(ctx -> {
							MinecraftClient mc = MinecraftClient.getInstance();
							mc.send(() -> openHistory(mc));
							return 1;
						}))));

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof DeathScreen) {
				final long openedAt = System.currentTimeMillis();
				ButtonWidget btn = ButtonWidget.builder(Text.translatable("deathdebrief.button"),
						b -> client.setScreen(new DebriefScreen(screen, 0)))
						.dimensions(scaledWidth / 2 - 100, scaledHeight / 4 + 120, 200, 20).build();
				btn.active = report != null && receivedAt >= openedAt - 5000;
				Screens.getButtons(screen).add(btn);
				ScreenEvents.afterTick(screen).register(s -> {
					if (!btn.active && report != null && receivedAt >= openedAt - 5000) {
						btn.active = true;
					}
				});
			}
		});
	}

	private static void open(MinecraftClient client) {
		if (DeathHistory.size() > 0) {
			client.setScreen(new DebriefScreen(client.currentScreen, 0));
		} else if (client.player != null) {
			client.player.sendMessage(Text.translatable("deathdebrief.no_report"), true);
		}
	}

	private static void openHistory(MinecraftClient client) {
		if (DeathHistory.size() > 0) {
			client.setScreen(new HistoryScreen(client.currentScreen));
		} else if (client.player != null) {
			client.player.sendMessage(Text.translatable("deathdebrief.no_report"), true);
		}
	}
}
