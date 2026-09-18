package com.fasterflight;

import com.fasterflight.config.FasterFlightConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;

/**
 * Applies and restores the flight speed multiplier on the local player.
 *
 * <p>Only {@link PlayerAbilities#getFlySpeed()} is touched. Vanilla derives both horizontal and
 * vertical flight movement from that single value, so scaling it also scales ascent and descent
 * without any extra velocity manipulation.
 *
 * <p>Nothing here is ever transmitted to the server: {@code abilities.flying} is left alone and no
 * ability packet is constructed. The client simply moves faster, and a server that rejects the
 * resulting position will correct it as it would any other movement.
 */
public final class FlightSpeedController {

    /** Vanilla's default flight speed, used only as a fallback if no base has been captured yet. */
    private static final float FALLBACK_BASE_FLY_SPEED = 0.05F;

    /**
     * The player's unmodified flight speed, captured before any multiplier is applied.
     * Snapshotted rather than hard-coded so other mods that legitimately change flight speed
     * are respected and correctly restored.
     */
    private static Float baseFlySpeed = null;

    /** Whether the multiplier is currently applied, so the base is only restored once. */
    private static boolean boosted = false;

    private FlightSpeedController() {
    }

    /**
     * Runs once per client tick.
     *
     * @param client       the Minecraft client instance
     * @param boostHeld    whether the hold-to-boost keybind is currently down
     */
    public static void tick(MinecraftClient client, boolean boostHeld) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            // Left the world; forget the snapshot so the next world captures a fresh base.
            reset();
            return;
        }

        PlayerAbilities abilities = player.getAbilities();

        if (!boostHeld) {
            restore(abilities);
            return;
        }

        if (baseFlySpeed == null) {
            // First boost of this world: remember the speed vanilla (or another mod) had set.
            baseFlySpeed = abilities.getFlySpeed();
        }

        double multiplier = FasterFlightConfig.getMultiplier();
        // Re-asserted every tick so a server-sent abilities packet cannot silently cancel the boost.
        abilities.setFlySpeed((float) (baseFlySpeed * multiplier));
        boosted = true;
    }

    /** @return whether the multiplier is currently applied, for the HUD indicator. */
    public static boolean isBoosting() {
        return boosted;
    }

    /** Restores the captured base flight speed if a boost is active. */
    private static void restore(PlayerAbilities abilities) {
        if (boosted) {
            abilities.setFlySpeed(baseFlySpeed != null ? baseFlySpeed : FALLBACK_BASE_FLY_SPEED);
            boosted = false;
        }
    }

    /** Clears all captured state. Called when the player leaves the world. */
    public static void reset() {
        baseFlySpeed = null;
        boosted = false;
    }
}
