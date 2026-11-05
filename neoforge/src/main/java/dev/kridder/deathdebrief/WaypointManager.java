package dev.kridder.deathdebrief;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.fml.ModList;

/** Routes a death point to Xaero's Minimap (chat link) and/or the built-in marker. */
public final class WaypointManager {
	private static final long DESPAWN_MS = 300_000L;

	private WaypointManager() {
	}

	private static boolean hasXaero() {
		ModList l = ModList.get();
		return l.isLoaded("xaerominimap") || l.isLoaded("xaerobetterpvp");
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
		Minecraft mc = Minecraft.getInstance();
		if (r == null) {
			return;
		}
		boolean builtinOnly = !hasXaero();
		if (fromButton && DeathWaypoint.isAt(r.x(), r.y(), r.z(), r.dimension())
				&& (builtinOnly || ClientConfig.builtinWaypoint.equals("always"))) {
			DeathWaypoint.clear("Waypoint button toggle");
			chat(mc, Component.translatable("deathdebrief.wp.removed").withStyle(ChatFormatting.GRAY));
			return;
		}
		boolean external = false;

		if (hasXaero()) {
			sendXaeroLink(mc, r);
			external = true;
		}

		String mode = ClientConfig.builtinWaypoint;
		boolean builtin = mode.equals("always") || (mode.equals("auto") && !external);
		if (builtin) {
			long created = fromButton ? System.currentTimeMillis() : r.receivedAt();
			if (System.currentTimeMillis() - created < DESPAWN_MS) {
				DeathWaypoint.set(r.x(), r.y(), r.z(), r.dimension(), created);
				chat(mc, Component.translatable("deathdebrief.wp.builtin").withStyle(ChatFormatting.GREEN));
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

	private static void sendXaeroLink(Minecraft mc, DeathRecord r) {
		String payload = "Death:D:" + r.x() + ":" + r.y() + ":" + r.z() + ":12:false:0:" + xaeroSet(r.dimension());
		MutableComponent msg = Component.translatable("deathdebrief.wp.xaero")
				.withStyle(s -> s.withColor(ChatFormatting.GOLD).withUnderlined(true)
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xaero_waypoint_add:" + payload))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("xaero-waypoint:" + payload))));
		chat(mc, msg);
	}

	private static void chat(Minecraft mc, Component t) {
		if (mc != null && mc.gui != null) {
			mc.gui.getChat().addMessage(t);
		}
	}
}
