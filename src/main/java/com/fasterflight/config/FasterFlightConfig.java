package com.fasterflight.config;

import com.fasterflight.FasterFlightMod;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

/**
 * Persistent settings for FasterFlight, stored at {@code config/fasterflight.json}.
 *
 * <p>The class is kept deliberately simple: a single numeric multiplier plus a couple of
 * display toggles. All mutation goes through the static helpers below so the value can never
 * escape the supported range, regardless of whether it came from the slider or the text box.
 */
@Config(name = FasterFlightConfig.NAME)
public class FasterFlightConfig implements ConfigData {

    public static final String NAME = "fasterflight";

    /** Lowest multiplier the user may select. A value of 1.0x is a no-op. */
    public static final double MIN_MULTIPLIER = 1.0D;

    /** Highest multiplier the user may select. */
    public static final double MAX_MULTIPLIER = 25.0D;

    /** Granularity of the slider and the manual text box. */
    public static final double STEP = 0.1D;

    /**
     * Flight speed multiplier applied while the boost key is held.
     * Rendered with a custom slider + text box entry rather than the default Autoconfig widget.
     */
    @ConfigEntry.Gui.Excluded
    public double speedMultiplier = 1.0D;

    /** Whether to draw the active multiplier above the hotbar while boosting. */
    public boolean showSpeedIndicator = true;

    // ------------------------------------------------------------------
    // Static access
    // ------------------------------------------------------------------

    private static ConfigHolder<FasterFlightConfig> holder;

    /** Registers the config with Cloth Config. Must be called once, from the mod initializer. */
    public static void register() {
        holder = AutoConfig.register(FasterFlightConfig.class, GsonConfigSerializer::new);
    }

    /** @return the live config instance, or a throwaway default if {@link #register()} has not run. */
    public static FasterFlightConfig get() {
        return holder != null ? holder.getConfig() : new FasterFlightConfig();
    }

    /** @return the current multiplier, clamped to the supported range. */
    public static double getMultiplier() {
        return clamp(get().speedMultiplier);
    }

    /**
     * Stores a new multiplier and writes it to disk immediately.
     *
     * <p>Saving eagerly keeps the file in step with the GUI, which matters because the text box
     * commits on Enter and on focus loss rather than only when the screen closes.
     *
     * @param value the desired multiplier; clamped and rounded to {@link #STEP}
     */
    public static void setMultiplier(double value) {
        FasterFlightConfig config = get();
        config.speedMultiplier = clamp(value);
        save();
    }

    /** Flushes the current config to {@code config/fasterflight.json}. */
    public static void save() {
        if (holder != null) {
            holder.save();
        }
    }

    /**
     * Clamps a value to the supported range and snaps it to the nearest {@link #STEP}.
     *
     * @param value the raw value, possibly out of range or unrounded
     * @return a value in {@code [MIN_MULTIPLIER, MAX_MULTIPLIER]} that is a whole number of steps
     */
    public static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return MIN_MULTIPLIER;
        }
        double stepped = Math.round(value / STEP) * STEP;
        // Rounding again removes binary floating point noise such as 3.9000000000000004.
        stepped = Math.round(stepped * 10.0D) / 10.0D;
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, stepped));
    }

    /**
     * Validates the value after Cloth Config deserializes it, so a hand-edited or corrupted
     * config file cannot put the player into an unusable state.
     */
    @Override
    public void validatePostLoad() {
        this.speedMultiplier = clamp(this.speedMultiplier);
    }
}
