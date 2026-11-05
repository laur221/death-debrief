package dev.kridder.deathdebrief;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Routes a death point to JourneyMap, Xaero's Minimap (chat link) and/or the built-in marker. */
public final class WaypointManager {
	private static final long DESPAWN_MS = 300_000L;

	private WaypointManager() {
	}

	private static boolean hasJourneyMap() {
		return FabricLoader.getInstance().isModLoaded("journeymap");
	}

	private static boolean hasXaero() {
		FabricLoader l = FabricLoader.getInstance();
		return l.isModLoaded("xaerominimap") || l.isModLoaded("xaerobetterpvp");
	}

	/** Called for every new death report. */
	public static void onDeath(DeathRecord r) {
		if (!ClientConfig.autoWaypoint) {
			return;
		}
		place(r, false);
	}

	/**
	 * Places a waypoint for the record and reports to chat where it went.
	 * Pressing the button on the already-shown built-in marker toggles it off.
	 */
	public static void place(DeathRecord r, boolean fromButton) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (r == null) {
			return;
		}
		boolean builtinOnly = !hasJourneyMap() && !hasXaero();
		if (fromButton && DeathWaypoint.isAt(r.x(), r.y(), r.z(), r.dimension())
				&& (builtinOnly || ClientConfig.builtinWaypoint.equals("always"))) {
			DeathWaypoint.clear("Waypoint button toggle");
			chat(mc, Text.translatable("deathdebrief.wp.removed").formatted(Formatting.GRAY));
			return;
		}
		boolean external = false;
		String name = "Death " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date());

		if (hasJourneyMap()) {
			if (JourneyMapBridge.add(name, r.x(), r.y(), r.z(), r.dimension())) {
				external = true;
				chat(mc, Text.translatable("deathdebrief.wp.journeymap").formatted(Formatting.GREEN));
			}
		}
		if (!external && hasXaero()) {
			sendXaeroLink(mc, r);
			external = true;
		}

		String mode = ClientConfig.builtinWaypoint;
		boolean builtin = mode.equals("always") || (mode.equals("auto") && !external);
		if (builtin) {
			long created = fromButton ? System.currentTimeMillis() : r.receivedAt();
			if (System.currentTimeMillis() - created < DESPAWN_MS) {
				DeathWaypoint.set(r.x(), r.y(), r.z(), r.dimension(), created);
				chat(mc, Text.translatable("deathdebrief.wp.builtin").formatted(Formatting.GREEN));
			}
		}
	}

	private static String xaeroSet(String dim) {
		return switch (dim) {
		case "minecraft:overworld" -> "Internal-overworld-waypoints";
		case "minecraft:the_nether" -> "Internal-the-nether-waypoints";
		case "minecraft:the_end" -> "Internal-the-end-waypoints";
		default -> {
			int i = dim.indexOf(':');
			String ns = i < 0 ? "minecraft" : dim.substring(0, i);
			String path = i < 0 ? dim : dim.substring(i + 1);
			yield "Internal-dim%" + ns + "$" + path + "-waypoints";
		}
		};
	}

	private static void sendXaeroLink(MinecraftClient mc, DeathRecord r) {
		String payload = "Death:D:" + r.x() + ":" + r.y() + ":" + r.z() + ":12:false:0:" + xaeroSet(r.dimension());
		MutableText msg = Text.translatable("deathdebrief.wp.xaero")
				.styled(s -> s.withColor(Formatting.GOLD).withUnderline(true)
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xaero_waypoint_add:" + payload))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("xaero-waypoint:" + payload))));
		chat(mc, msg);
	}

	private static void chat(MinecraftClient mc, Text t) {
		if (mc != null && mc.inGameHud != null) {
			mc.inGameHud.getChatHud().addMessage(t);
		}
	}
}
