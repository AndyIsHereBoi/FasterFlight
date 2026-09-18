package com.fasterflight.config;

import com.fasterflight.FasterFlightMod;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

/**
 * Persistent settings, stored at {@code config/fasterflight.json}.
 *
 * <p>All writes go through the static helpers below so the value stays inside the supported range
 * whichever caller set it.
 */
@Config(name = FasterFlightConfig.NAME)
public class FasterFlightConfig implements ConfigData {

    public static final String NAME = "fasterflight";

    public static final double MIN_MULTIPLIER = 1.0D;
    public static final double MAX_MULTIPLIER = 25.0D;
    public static final double STEP = 0.1D;

    /** Rendered by a custom slider entry instead of the default Autoconfig widget. */
    @ConfigEntry.Gui.Excluded
    public double speedMultiplier = 1.0D;

    public boolean showSpeedIndicator = true;

    private static ConfigHolder<FasterFlightConfig> holder;

    public static void register() {
        holder = AutoConfig.register(FasterFlightConfig.class, GsonConfigSerializer::new);
    }

    /** @return the live config, or a throwaway default if {@link #register()} has not run yet. */
    public static FasterFlightConfig get() {
        return holder != null ? holder.getConfig() : new FasterFlightConfig();
    }

    public static double getMultiplier() {
        return clamp(get().speedMultiplier);
    }

    /** Also writes to disk, because the text box commits before the screen closes. */
    public static void setMultiplier(double value) {
        FasterFlightConfig config = get();
        config.speedMultiplier = clamp(value);
        save();
    }

    public static void save() {
        if (holder != null) {
            holder.save();
        }
    }

    /**
     * Clamps to the supported range and snaps to the nearest {@link #STEP}.
     */
    public static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return MIN_MULTIPLIER;
        }
        double stepped = Math.round(value / STEP) * STEP;
        // Rounding twice removes binary floating point noise such as 3.9000000000000004.
        stepped = Math.round(stepped * 10.0D) / 10.0D;
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, stepped));
    }

    /** Guards against a hand-edited or corrupted config file. */
    @Override
    public void validatePostLoad() {
        this.speedMultiplier = clamp(this.speedMultiplier);
    }
}
