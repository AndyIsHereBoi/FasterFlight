package com.fasterflight;

import com.fasterflight.config.FasterFlightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;

/**
 * Applies and restores the flight speed multiplier on the local player.
 *
 * <p>Only {@link Abilities#getFlyingSpeed()} is touched, which vanilla uses for both horizontal and
 * vertical flight, so ascent and descent scale without extra velocity manipulation. Nothing is sent
 * to the server, and {@code abilities.flying} is left alone.
 */
public final class FlightSpeedController {

    private static final float FALLBACK_BASE_FLY_SPEED = 0.05F;

    /**
     * The player's unmodified flight speed. Snapshotted rather than hard-coded so a speed set by
     * another mod is restored correctly.
     */
    private static Float baseFlySpeed = null;

    private static boolean boosted = false;

    private FlightSpeedController() {
    }

    public static void tick(Minecraft client, boolean boostHeld) {
        LocalPlayer player = client.player;
        if (player == null) {
            // Left the world, so the next world captures a fresh base.
            reset();
            return;
        }

        Abilities abilities = player.getAbilities();

        if (!boostHeld) {
            restore(abilities);
            return;
        }

        if (baseFlySpeed == null) {
            baseFlySpeed = abilities.getFlyingSpeed();
        }

        // Re-asserted every tick because a server-sent abilities packet would otherwise cancel it.
        abilities.setFlyingSpeed((float) (baseFlySpeed * FasterFlightConfig.getMultiplier()));
        boosted = true;
    }

    public static boolean isBoosting() {
        return boosted;
    }

    private static void restore(Abilities abilities) {
        if (boosted) {
            abilities.setFlyingSpeed(baseFlySpeed != null ? baseFlySpeed : FALLBACK_BASE_FLY_SPEED);
            boosted = false;
        }
    }

    /** Called when the player leaves the world. */
    public static void reset() {
        baseFlySpeed = null;
        boosted = false;
    }
}
