package io.mewb.nekioHolos;


import io.mewb.nekioHolos.holograms.PlayerHologram;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NekioHolos extends JavaPlugin {

    private static FollowHoloPlugin instance;
    private final Map<UUID, PlayerHologram> activeHolograms = new ConcurrentHashMap<>();
    private HologramFollowTask followTask;

    private Vector hologramOffset;
    private int updateInterval;
    private int protocolVersion119_4;

    private boolean placeholderApiAvailable = false;
    private boolean fancyHologramsAvailable = false;
    private boolean decentHologramsAvailable = false;

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration
        saveDefaultConfig();
        loadConfiguration();

        // Check for dependencies
        PluginManager pm = Bukkit.getPluginManager();
        if (pm.getPlugin("PlaceholderAPI") != null) {
            placeholderApiAvailable = true;
            getLogger().info("Successfully hooked into PlaceholderAPI.");
        } else {
            getLogger().info("PlaceholderAPI not found, placeholders will not be parsed.");
        }

        if (pm.getPlugin("FancyHolograms") != null) {
            try {
                // Attempt to access an API class to ensure it's loaded correctly
                Class.forName("de.oliver.fancyholograms.api.Hologram");
                fancyHologramsAvailable = true;
                getLogger().info("Successfully hooked into FancyHolograms.");
            } catch (ClassNotFoundException e) {
                getLogger().severe("FancyHolograms plugin is present, but API classes could not be found! FancyHolograms will not be used.");
                fancyHologramsAvailable = false;
            }
        } else {
            getLogger().warning("FancyHolograms not found. Modern hologram features will be unavailable.");
        }

        if (pm.getPlugin("DecentHolograms") != null) {
            try {
                Class.forName("eu.decentsoftware.holograms.api.DHAPI");
                decentHologramsAvailable = true;
                getLogger().info("Successfully hooked into DecentHolograms.");
            } catch (ClassNotFoundException e) {
                getLogger().severe("DecentHolograms plugin is present, but API classes could not be found! DecentHolograms will not be used.");
                decentHologramsAvailable = false;
            }
        } else {
            getLogger().warning("DecentHolograms not found. Fallback hologram features will be unavailable.");
        }

        if (!fancyHologramsAvailable && !decentHologramsAvailable) {
            getLogger().severe("Neither FancyHolograms nor DecentHolograms found! This plugin requires at least one to function.");
            pm.disablePlugin(this);
            return;
        }

        // Register command
        getCommand("holomessage").setExecutor(new HoloMessageCommand(this));

        // Register event listener
        pm.registerEvents(new PlayerEventListener(this), this);

        // Start hologram follow task
        followTask = new HologramFollowTask(this);
        followTask.runTaskTimer(this, 0L, updateInterval);

        getLogger().info("FollowHolo plugin has been enabled.");
    }

    @Override
    public void onDisable() {
        // Stop task
        if (followTask != null) {
            followTask.cancel();
        }

        // Clear all active holograms
        for (PlayerHologram hologram : activeHolograms.values()) {
            hologram.delete();
        }
        activeHolograms.clear();

        getLogger().info("FollowHolo plugin has been disabled.");
    }

    public void loadConfiguration() {
        reloadConfig(); // Reloads the config from disk
        FileConfiguration config = getConfig();

        double x = config.getDouble("hologram-offset.x", 0.0);
        double y = config.getDouble("hologram-offset.y", 0.6);
        double z = config.getDouble("hologram-offset.z", 1.5);
        hologramOffset = new Vector(x, y, z);

        updateInterval = config.getInt("update-interval", 2);
        if (updateInterval < 1) {
            getLogger().warning("Update interval is too low, setting to 1 tick.");
            updateInterval = 1;
        }
        protocolVersion119_4 = config.getInt("protocol-version-1_19_4", 762);
    }

    public static FollowHoloPlugin getInstance() {
        return instance;
    }

    public Map<UUID, PlayerHologram> getActiveHolograms() {
        return activeHolograms;
    }

    public Vector getHologramOffset() {
        return hologramOffset;
    }

    public boolean isPlaceholderApiAvailable() {
        return placeholderApiAvailable;
    }

    public boolean isFancyHologramsAvailable() {
        return fancyHologramsAvailable;
    }

    public boolean isDecentHologramsAvailable() {
        return decentHologramsAvailable;
    }

    public int getProtocolVersion119_4() {
        return protocolVersion119_4;
    }

    public String parsePlaceholders(Player player, String text) {
        if (placeholderApiAvailable && player != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    /**
     * Calculates the target location for the hologram based on player's eye location and configured offset,
     * relative to the player's direction.
     * @param player The player
     * @return The target location for the hologram
     */
    public Location calculateHologramLocation(Player player) {
        Location playerEyeLoc = player.getEyeLocation();
        Vector direction = playerEyeLoc.getDirection().normalize();

        // Calculate right vector (cross product of direction and up vector)
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        if (right.lengthSquared() == 0) { // Player is looking straight up or down
            // Use player's yaw to determine a "right" vector.
            // Get the direction vector in the XZ plane
            Vector xzDirection = new Vector(direction.getX(), 0, direction.getZ()).normalize();
            if (xzDirection.lengthSquared() > 0) {
                right = xzDirection.clone().crossProduct(new Vector(0, 1, 0)).normalize();
            } else {
                // Fallback if player is looking perfectly up/down and xzDirection is zero
                // Get yaw, convert to radians, then use sin/cos for a horizontal vector
                float yaw = playerEyeLoc.getYaw();
                double yawRad = Math.toRadians(yaw);
                right = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize().crossProduct(new Vector(0,1,0)).normalize();
            }
        }


        // Calculate up vector (relative to player's head, usually world UP is fine for Y offset unless complex tilting is needed)
        // For simplicity, we use world UP for Y offset from eye level.
        // If you want Y offset to be perpendicular to player's view direction (like a HUD element):
        // Vector up = right.clone().crossProduct(direction).normalize();
        // For now, Y offset is just added to the eye location's Y.

        Location targetLoc = playerEyeLoc.clone();

        // Apply Z offset (forward/backward)
        targetLoc.add(direction.clone().multiply(hologramOffset.getZ()));

        // Apply X offset (left/right)
        targetLoc.add(right.clone().multiply(hologramOffset.getX()));

        // Apply Y offset (up/down from eye level)
        targetLoc.add(0, hologramOffset.getY(), 0); // Simple Y addition to eye height

        return targetLoc;
    }
}