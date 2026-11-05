package dev.kridder.deathdebrief;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;

/** Client-side per-world / per-server death history, newest first. */
public final class DeathHistory {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<DeathRecord>>() {
	}.getType();
	private static final List<DeathRecord> LIST = new ArrayList<>();

	/** The newest record received during this play session (null until a death arrives). */
	public static DeathRecord sessionRecord;
	private static String id;

	private DeathHistory() {
	}

	private static Path configDir() {
		return FabricLoader.getInstance().getGameDir().resolve("config").resolve("deathdebrief");
	}

	private static String sanitize(String s) {
		String t = s == null ? "" : s.replaceAll("[^A-Za-z0-9._-]", "_");
		return t.isEmpty() ? "unknown" : t;
	}

	private static String resolveId(MinecraftClient c) {
		try {
			IntegratedServer s = c.getServer();
			if (s != null) {
				return "sp_" + sanitize(s.getSaveProperties().getLevelName());
			}
			ServerInfo e = c.getCurrentServerEntry();
			if (e != null) {
				return "mp_" + sanitize(e.address);
			}
		} catch (Throwable t) {
			// fall through
		}
		return "mp_unknown";
	}

	private static int limit() {
		int def = 50;
		Path f = configDir().resolve("client.json");
		try {
			if (Files.exists(f)) {
				JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
				if (o.has("historySize")) {
					return Math.max(1, o.get("historySize").getAsInt());
				}
			} else {
				Files.createDirectories(f.getParent());
				Files.writeString(f, "{\n  \"historySize\": " + def + "\n}\n", StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			// use default
		}
		return def;
	}

	private static Path file() {
		return configDir().resolve("history").resolve(id + ".json");
	}

	public static void load(MinecraftClient c) {
		LIST.clear();
		sessionRecord = null;
		id = resolveId(c);
		try {
			Path f = file();
			if (Files.exists(f)) {
				List<DeathRecord> l = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), LIST_TYPE);
				if (l != null) {
					for (DeathRecord r : l) {
						if (r != null) {
							LIST.add(r);
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[DeathDebrief] failed to load history: " + e);
		}
	}

	public static void add(DeathRecord r) {
		if (id == null) {
			load(MinecraftClient.getInstance());
		}
		LIST.add(0, r);
		sessionRecord = r;
		int lim = limit();
		while (LIST.size() > lim) {
			LIST.remove(LIST.size() - 1);
		}
		save();
	}

	public static void clear() {
		LIST.clear();
		save();
	}

	private static void save() {
		if (id == null) {
			return;
		}
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(LIST, LIST_TYPE), StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.err.println("[DeathDebrief] failed to save history: " + e);
		}
	}

	public static int size() {
		return LIST.size();
	}

	public static DeathRecord get(int i) {
		return i >= 0 && i < LIST.size() ? LIST.get(i) : null;
	}
}
