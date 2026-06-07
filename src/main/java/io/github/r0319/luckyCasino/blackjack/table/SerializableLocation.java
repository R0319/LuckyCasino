package io.github.r0319.luckyCasino.blackjack.table;

import org.bukkit.Bukkit;
import org.bukkit.Location;

/**
 * A JSON-serializable representation of a Bukkit {@link Location}.
 * Gson handles field serialization automatically (no annotations needed).
 */
public class SerializableLocation {

    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    /** No-arg constructor required by Gson. */
    public SerializableLocation() {}

    public SerializableLocation(Location loc) {
        this.world = loc.getWorld().getName();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.yaw = loc.getYaw();
        this.pitch = loc.getPitch();
    }

    /**
     * Converts back to a Bukkit {@link Location}.
     * Pitch is always 0 (horizontal) regardless of what was stored — this prevents
     * NPCs or teleported players from facing straight down if the position was set
     * while the admin was looking downward.
     */
    public Location toLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, 0f);
    }

    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    @Override
    public String toString() {
        return String.format("%.2f, %.2f, %.2f (%s)", x, y, z, world);
    }
}
