package io.mewb.nekioHolos.holograms;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.List;

public interface PlayerHologram {
    /**
     * Creates and spawns the hologram for the target player.
     * @param targetPlayer The player who will see the hologram.
     * @param initialLocation The initial location to spawn the hologram.
     * @param lines The text lines for the hologram.
     */
    void create(Player targetPlayer, Location initialLocation, List<String> lines);

    /**
     * Updates the text lines of the hologram.
     * @param lines New list of lines.
     */
    void updateLines(List<String> lines);

    /**
     * Moves the hologram to a new location.
     * @param newLocation The new location.
     */
    void updateLocation(Location newLocation);

    /**
     * Deletes the hologram, removing it from view and cleaning up resources.
     */
    void delete();

    /**
     * Checks if the hologram is currently spawned and active.
     * @return true if spawned, false otherwise.
     */
    boolean isSpawned();

    /**
     * Gets the player this hologram is associated with.
     * @return The player.
     */
    Player getPlayer();
}