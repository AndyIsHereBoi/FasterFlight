package com.fasterflight.hud;

import com.fasterflight.FlightSpeedController;
import com.fasterflight.config.FasterFlightConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Draws the active multiplier just above the hotbar while the boost is being applied.
 *
 * <p>Registered through {@link HudElementRegistry} rather than the old {@code HudRenderCallback},
 * which no longer exists in this Minecraft version.
 */
public final class BoostIndicatorRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fasterflight");

    private static final Identifier ELEMENT_ID =
            Identifier.fromNamespaceAndPath("fasterflight", "boost_indicator");

    private static final int HOTBAR_CLEARANCE = 55;
    private static final int TEXT_COLOR = 0xFFFFC24A;

    private BoostIndicatorRenderer() {
    }

    public static void register() {
        HudElementRegistry.addLast(ELEMENT_ID, BoostIndicatorRenderer::extractRenderState);
    }

    private static void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker delta) {
        if (!FlightSpeedController.isBoosting() || !FasterFlightConfig.get().showSpeedIndicator) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }

        // This runs on the render thread every frame while the indicator is visible. A cosmetic
        // overlay must never be able to take the game down, so any failure here is swallowed and
        // simply skips drawing for that frame rather than propagating into the render loop.
        try {
            String label = String.format(Locale.ROOT, "%.1fx", FasterFlightConfig.getMultiplier());

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int textWidth = client.font.width(label);

            int x = (screenWidth - textWidth) / 2;
            int y = screenHeight - HOTBAR_CLEARANCE;

            extractor.text(client.font, Component.literal(label), x, y, TEXT_COLOR, true);
        } catch (RuntimeException e) {
            // Logged once per distinct failure rather than every frame, so a persistent problem
            // does not flood the log.
            reportOnce(e);
        }
    }

    /** The last failure already logged, so repeated per-frame failures stay quiet. */
    private static String lastReported;

    /**
     * Logs a render failure at most once per distinct message.
     *
     * @param error the failure to report
     */
    private static void reportOnce(RuntimeException error) {
        String message = error.getClass().getName() + ": " + error.getMessage();
        if (message.equals(lastReported)) {
            return;
        }
        lastReported = message;
        LOGGER.warn("FasterFlight could not draw the flight speed indicator; skipping it", error);
    }
}
