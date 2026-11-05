package dev.kridder.deathdebrief;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.util.math.BlockPos;

/** JourneyMap integration. Only ever class-loaded when the "journeymap" mod is present. */
@journeymap.api.v2.common.JourneyMapPlugin(apiVersion = "2.0.0")
public class JourneyMapPlugin implements IClientPlugin {
	private static IClientAPI api;

	@Override
	public void initialize(IClientAPI jmClientApi) {
		api = jmClientApi;
	}

	@Override
	public String getModId() {
		return "deathdebrief";
	}

	/** Creates a death waypoint in JourneyMap; returns false if JourneyMap is not ready or fails. */
	public static boolean add(String name, int x, int y, int z, String dimension) {
		if (api == null) {
			return false;
		}
		try {
			Waypoint wp = WaypointFactory.createWaypoint("deathdebrief", new BlockPos(x, y, z), dimension, true);
			wp.setName(name);
			wp.setColor(0xFF2222);
			api.addWaypoint("deathdebrief", wp);
			return true;
		} catch (Throwable t) {
			System.err.println("[DeathDebrief] JourneyMap waypoint failed: " + t);
			return false;
		}
	}
}
