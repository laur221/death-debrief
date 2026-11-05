package dev.kridder.deathdebrief;

import java.nio.file.Files;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Dev-only automatic in-game test. Active only with -Ddeathdebrief.selftest=true. */
public final class SelfTest {
	private static final String TAG = "[DD-SELFTEST] ";
	private static final String[] COMMANDS = {
			"gamerule keepInventory false",
			"give @s minecraft:diamond_sword",
			"item replace entity @s armor.head with minecraft:iron_helmet",
			"item replace entity @s armor.chest with minecraft:iron_chestplate",
			"give @s minecraft:cobblestone 32",
			"damage @s 4 minecraft:on_fire",
			"damage @s 6 minecraft:fall",
			"summon minecraft:zombie ~2 ~ ~",
			"damage @s 30 minecraft:lava"
	};

	private enum Phase { TITLE, WORLD_WAIT, COMMANDS, DEATH_WAIT, SHOT1, SHOT2, SCALE2, SCALE3, RESPAWN, SHOT_AFTER, D2_CMD, D2_WAIT, R2, D3_CMD, D3_WAIT, R3, HIST_SHOT, ENTRY_SHOT, DONE }

	private static Phase phase = Phase.TITLE;
	private static int ticks;
	private static int worldTicks;
	private static int cmdIndex;
	private static int originalScale = -1;
	private static boolean worldRequested;
	private static boolean waitingLogged;
	private static DeathRecord mark;

	private SelfTest() {
	}

	public static void init() {
		if (!"true".equals(System.getProperty("deathdebrief.selftest"))) {
			return;
		}
		log("enabled");
		ClientTickEvents.END_CLIENT_TICK.register(SelfTest::tick);
	}

	private static void log(String s) {
		System.out.println(TAG + s);
	}

	private static void tick(MinecraftClient client) {
		try {
			step(client);
		} catch (Throwable t) {
			log("exception in phase " + phase + ": " + t);
			t.printStackTrace();
		}
	}

	private static void go(Phase p) {
		phase = p;
		ticks = 0;
		log("phase -> " + p);
	}

	private static void shot(MinecraftClient client, String name) {
		try {
			ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), msg -> {});
			log("screenshot " + name);
		} catch (Throwable t) {
			log("screenshot failed " + name + ": " + t);
		}
	}

	private static void openDebrief(MinecraftClient client) {
		if (DeathHistory.size() == 0) {
			log("no report available to open");
			return;
		}
		client.setScreen(new DebriefScreen(client.currentScreen, 0));
	}

	private static void step(MinecraftClient client) {
		ticks++;
		switch (phase) {
		case TITLE -> {
			if (client.player != null) {
				go(Phase.WORLD_WAIT);
				return;
			}
			if (client.currentScreen instanceof TitleScreen && !worldRequested && ticks > 60) {
				worldRequested = true;
				try {
					if (Files.isDirectory(client.runDirectory.toPath().resolve("saves").resolve("ddtest"))) {
						log("loading existing world ddtest");
						client.createIntegratedServerLoader().start("ddtest", () -> {});
					} else {
						log("creating world ddtest");
						LevelInfo info = new LevelInfo("ddtest", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
								new GameRules(), DataConfiguration.SAFE_MODE);
						client.createIntegratedServerLoader().createAndStart("ddtest", info,
								GeneratorOptions.createRandom(), WorldPresets::createDemoOptions,
								client.currentScreen);
					}
				} catch (Throwable t) {
					log("world load/create failed: " + t);
					t.printStackTrace();
				}
			} else if (!waitingLogged && ticks > 200) {
				waitingLogged = true;
				log("waiting for world");
			}
		}
		case WORLD_WAIT -> {
			if (client.player == null) {
				return;
			}
			worldTicks++;
			if (worldTicks >= 100) {
				go(Phase.COMMANDS);
			}
		}
		case COMMANDS -> {
			if (client.player == null) {
				return;
			}
			if (client.currentScreen instanceof DeathScreen) {
				go(Phase.DEATH_WAIT);
				return;
			}
			if (ticks % 30 == 0 && cmdIndex < COMMANDS.length) {
				String c = COMMANDS[cmdIndex++];
				log("command: " + c);
				client.player.networkHandler.sendChatCommand(c);
			} else if (cmdIndex >= COMMANDS.length && ticks > 30 * (COMMANDS.length + 8)) {
				log("player never died; aborting");
				go(Phase.DONE);
			}
		}
		case DEATH_WAIT -> {
			if (client.currentScreen instanceof DeathScreen) {
				if (DeathDebriefClient.report != null && System.currentTimeMillis() - DeathDebriefClient.receivedAt >= 2000 && ticks >= 40) {
					shot(client, "dd_deathscreen.png");
					go(Phase.SHOT1);
				}
			} else {
				ticks = 0;
			}
		}
		case SHOT1 -> {
			openDebrief(client);
			go(Phase.SHOT2);
		}
		case SHOT2 -> {
			if (ticks >= 40) {
				shot(client, "dd_debrief_gui_auto.png");
				originalScale = client.options.getGuiScale().getValue();
				client.options.getGuiScale().setValue(2);
				client.onResolutionChanged();
				go(Phase.SCALE2);
			}
		}
		case SCALE2 -> {
			if (ticks >= 40) {
				shot(client, "dd_debrief_gui2.png");
				client.options.getGuiScale().setValue(3);
				client.onResolutionChanged();
				go(Phase.SCALE3);
			}
		}
		case SCALE3 -> {
			if (ticks >= 40) {
				shot(client, "dd_debrief_gui3.png");
				client.options.getGuiScale().setValue(originalScale);
				client.onResolutionChanged();
				client.setScreen(null);
				if (client.player != null) {
					client.player.requestRespawn();
				}
				go(Phase.RESPAWN);
			}
		}
		case RESPAWN -> {
			if (ticks >= 60) {
				client.setScreen(null);
				openDebrief(client);
				go(Phase.SHOT_AFTER);
			}
		}
		case SHOT_AFTER -> {
			if (ticks >= 20) {
				shot(client, "dd_after_respawn.png");
				client.setScreen(null);
				mark = DeathHistory.sessionRecord;
				go(Phase.D2_CMD);
			}
		}
		case D2_CMD -> {
			if (ticks >= 20) {
				log("command: damage @s 40 minecraft:lava");
				client.player.networkHandler.sendChatCommand("damage @s 40 minecraft:lava");
				go(Phase.D2_WAIT);
			}
		}
		case D2_WAIT -> {
			if (client.currentScreen instanceof DeathScreen && DeathHistory.sessionRecord != mark && ticks >= 40) {
				mark = DeathHistory.sessionRecord;
				client.player.requestRespawn();
				go(Phase.R2);
			} else if (ticks > 400) {
				log("second death never arrived");
				go(Phase.DONE);
			}
		}
		case R2 -> {
			if (ticks >= 60) {
				client.setScreen(null);
				go(Phase.D3_CMD);
			}
		}
		case D3_CMD -> {
			if (ticks >= 20) {
				log("command: kill @s");
				client.player.networkHandler.sendChatCommand("kill @s");
				go(Phase.D3_WAIT);
			}
		}
		case D3_WAIT -> {
			if (client.currentScreen instanceof DeathScreen && DeathHistory.sessionRecord != mark && ticks >= 40) {
				mark = DeathHistory.sessionRecord;
				client.player.requestRespawn();
				go(Phase.R3);
			} else if (ticks > 400) {
				log("third death never arrived");
				go(Phase.DONE);
			}
		}
		case R3 -> {
			if (ticks >= 60) {
				client.setScreen(null);
				client.setScreen(new HistoryScreen(null));
				go(Phase.HIST_SHOT);
			}
		}
		case HIST_SHOT -> {
			if (ticks >= 40) {
				shot(client, "dd_history.png");
				HistoryScreen hs = client.currentScreen instanceof HistoryScreen h ? h : new HistoryScreen(null);
				client.setScreen(new DebriefScreen(hs, Math.min(1, Math.max(0, DeathHistory.size() - 1))));
				go(Phase.ENTRY_SHOT);
			}
		}
		case ENTRY_SHOT -> {
			if (ticks >= 40) {
				shot(client, "dd_history_entry.png");
				log("DONE");
				go(Phase.DONE);
			}
		}		case DONE -> {
		}
		}
	}
}
