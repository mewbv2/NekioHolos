// src/main/java/com/example/followholo/holograms/FancyPlayerHologram.java
package io.mewb.nekioHolos.holograms;


import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancyholograms.api.data.HologramData;
import de.oliver.fancyholograms.api.hologram.HologramType;
import de.oliver.fancyholograms.api.data.TextHologramData; // For casting DisplayData
import de.oliver.fancyholograms.api.HologramManager;

import io.mewb.nekioHolos.NekioHolos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage; // For converting string to Component

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.stream.Collectors;
// No need for Collections import if not used for emptySet

public class FancyPlayerHologram implements PlayerHologram {

    private final NekioHolos plugin;
    private Hologram fancyHologram;
    private Player targetPlayer;
    private String hologramName; // Unique name for this hologram instance
    private boolean spawned = false; // Internal flag to track if we consider it active

    public FancyPlayerHologram(NekioHolos plugin) {
        this.plugin = plugin;
    }

    @Override
    public void create(Player targetPlayer, Location initialLocation, List<String> lines) {
        this.targetPlayer = targetPlayer;
        // Generate a unique name for the hologram
        this.hologramName = "followholo_fancy_" + targetPlayer.getUniqueId().toString() + "_" + System.currentTimeMillis();

        HologramManager hologramManager = FancyHologramsPlugin.get().getHologramManager();

        // Ensure any hologram with this name is removed first (shouldn't happen with timestamp, but good practice)
        hologramManager.getHologram(hologramName).ifPresent(hologramManager::removeHologram);

        // Process lines with PlaceholderAPI (these are still strings)
        List<String> processedStringLines = lines.stream()
                .map(line -> plugin.parsePlaceholders(targetPlayer, line))
                .collect(Collectors.toList());

        // Create HologramData. For TEXT type, the constructor can take List<String>.
        // FancyHolograms will handle converting these strings to Components internally, likely using MiniMessage.
        HologramData data = new HologramData(hologramName, HologramType.TEXT, (Location) processedStringLines);
        data.setLocation(initialLocation);
        data.addShownTo(targetPlayer.getUniqueId()); // Show only to this player
        data.setEnabled(true); // Ensure the hologram is active
        data.setPersistent(false); // IMPORTANT: Make it non-persistent so it doesn't save to file

        // Create the hologram via the manager. This also spawns/shows it.
        this.fancyHologram = hologramManager.create(data);

        if (this.fancyHologram != null && !this.fancyHologram.isDeleted()) {
            spawned = true;
            plugin.getLogger().info("Created FancyHologram '" + hologramName + "' for " + targetPlayer.getName());
        } else {
            spawned = false; // Ensure flag is false if creation failed
            plugin.getLogger().severe("Failed to create FancyHologram for " + targetPlayer.getName() + " with name " + hologramName + ". Result was null or marked as deleted immediately.");
        }
    }

    @Override
    public void updateLines(List<String> lines) {
        if (fancyHologram == null || fancyHologram.isDeleted() || targetPlayer == null || !spawned) {
            return;
        }

        // Process string lines with PlaceholderAPI
        List<String> processedStringLines = lines.stream()
                .map(line -> plugin.parsePlaceholders(targetPlayer, line))
                .collect(Collectors.toList());

        // Convert processed strings to Kyori Adventure Components using MiniMessage
        List<Component> componentLines = processedStringLines.stream()
                .map(line -> MiniMessage.miniMessage().deserialize(line))
                .collect(Collectors.toList());

        HologramData data = fancyHologram.getData();
        // For TEXT holograms, DisplayData should be TextHologramData
        if (data.getDisplayData() instanceof TextHologramData) {
            ((TextHologramData) data.getDisplayData()).setLines(componentLines);
            fancyHologram.queueUpdate(); // Queue an update to apply changes
        } else {
            plugin.getLogger().warning("Could not update lines for hologram '" + hologramName + "': DisplayData is not TextHologramData.");
        }
    }

    @Override
    public void updateLocation(Location newLocation) {
        if (fancyHologram == null || fancyHologram.isDeleted() || newLocation == null || targetPlayer == null || !targetPlayer.isOnline() || !spawned) {
            if ((targetPlayer == null || !targetPlayer.isOnline()) && spawned) {
                // Player logged off or became invalid, clean up
                delete();
            }
            return;
        }

        HologramData data = fancyHologram.getData();

        // Check for world changes. If world changes, FancyHolograms might handle it,
        // but for stability, recreating could be an option (more complex).
        // For now, we assume queueUpdate() after setLocation handles it.
        if (data.getLocation() != null && !newLocation.getWorld().equals(data.getLocation().getWorld())) {
            plugin.getLogger().warning("Hologram '" + hologramName + "' for " + targetPlayer.getName() +
                    " is changing worlds. Attempting direct move. If issues occur, recreation might be needed.");
        }

        data.setLocation(newLocation);
        fancyHologram.queueUpdate(); // Queue an update to apply changes
    }

    @Override
    public void delete() {
        // Ensure deletion happens on the main server thread if not already
        if (!plugin.getServer().isPrimaryThread()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    deleteInternal();
                }
            }.runTask(plugin);
        } else {
            deleteInternal();
        }
    }

    private void deleteInternal() {
        if (fancyHologram != null && !fancyHologram.isDeleted()) { // Check if not already deleted
            String playerNameForLog = (targetPlayer != null ? targetPlayer.getName() : "Unknown Player");
            plugin.getLogger().info("Attempting to delete FancyHologram '" + hologramName + "' for " + playerNameForLog);

            HologramManager hologramManager = FancyHologramsPlugin.get().getHologramManager();
            hologramManager.removeHologram(fancyHologram); // This should handle despawning and unregistering

            plugin.getLogger().info("Successfully requested deletion of FancyHologram '" + hologramName + "' for " + playerNameForLog);
        }
        // Always nullify and set spawned to false, even if it was already deleted.
        fancyHologram = null;
        spawned = false;
    }


    @Override
    public boolean isSpawned() {
        // Check our internal flag, the hologram object, its deleted status, and if the manager still knows it.
        // The manager check is a strong indicator if it's truly active in the FancyHolograms system.
        return spawned &&
                fancyHologram != null &&
                !fancyHologram.isDeleted() &&
                fancyHologram.getData().isEnabled() &&
                FancyHologramsPlugin.get().getHologramManager().getHologram(this.hologramName).isPresent();
    }

    @Override
    public Player getPlayer() {
        return targetPlayer;
    }
}
