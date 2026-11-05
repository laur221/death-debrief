package dev.kridder.deathdebrief;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/** Reads config/deathdebrief/client.json and fills in missing keys with defaults. */
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static boolean autoWaypoint = true;
	/** "auto" (only without a minimap mod), "always" or "never". */
	public static String builtinWaypoint = "auto";

	private ClientConfig() {
	}

	private static Path file() {
		return FMLPaths.GAMEDIR.get().resolve("config").resolve("deathdebrief").resolve("client.json");
	}

	public static void load() {
		Path f = file();
		JsonObject o = new JsonObject();
		try {
			if (Files.exists(f)) {
				o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			}
		} catch (Exception e) {
			o = new JsonObject();
		}
		boolean changed = false;
		if (!o.has("historySize")) {
			o.addProperty("historySize", 50);
			changed = true;
		}
		if (!o.has("autoWaypoint")) {
			o.addProperty("autoWaypoint", true);
			changed = true;
		}
		if (!o.has("builtinWaypoint")) {
			o.addProperty("builtinWaypoint", "auto");
			changed = true;
		}
		try {
			autoWaypoint = o.get("autoWaypoint").getAsBoolean();
		} catch (Exception e) {
			autoWaypoint = true;
		}
		try {
			String s = o.get("builtinWaypoint").getAsString().toLowerCase(java.util.Locale.ROOT);
			builtinWaypoint = (s.equals("always") || s.equals("never")) ? s : "auto";
		} catch (Exception e) {
			builtinWaypoint = "auto";
		}
		if (changed) {
			try {
				Files.createDirectories(f.getParent());
				Files.writeString(f, GSON.toJson(o) + "\n", StandardCharsets.UTF_8);
			} catch (Exception e) {
				System.err.println("[DeathDebrief] failed to write client.json: " + e);
			}
		}
	}
}
