package me.stackspawners;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;

public class SpawnerData {
    public final String world;
    public final int x, y, z;
    public final EntityType type;
    public int stack;
    public double exp;
    public int timer;
    public final Map<Material, Long> items = new EnumMap<>(Material.class);

    public SpawnerData(String world, int x, int y, int z, EntityType type, int stack) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.stack = stack;
    }

    public String key() {
        return key(world, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public long totalItems() {
        long t = 0;
        for (long v : items.values()) t += v;
        return t;
    }
}
