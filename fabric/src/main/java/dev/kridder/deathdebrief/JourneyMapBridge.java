package dev.kridder.deathdebrief;

/** Indirection so WaypointManager never references JourneyMap classes directly. */
final class JourneyMapBridge {
	private JourneyMapBridge() {
	}

	static boolean add(String name, int x, int y, int z, String dimension) {
		try {
			return JourneyMapPlugin.add(name, x, y, z, dimension);
		} catch (Throwable t) {
			return false;
		}
	}
}
