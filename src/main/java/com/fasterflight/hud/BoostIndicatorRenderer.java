package com.fasterflight.hud;

import com.fasterflight.FlightSpeedController;
import com.fasterflight.config.FasterFlightConfig;
import com.fasterflight.config.TextCompat;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the active multiplier just above the hotbar while the boost is being applied.
 */
public final class BoostIndicatorRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fasterflight");

    private static final int HOTBAR_CLEARANCE = 55;
    private static final int TEXT_COLOR = 0xFFFFC24A;

    private BoostIndicatorRenderer() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(BoostIndicatorRenderer::onRenderHud);
    }

    private static void onRenderHud(MatrixStack matrices, float tickDelta) {
        if (!FlightSpeedController.isBoosting() || !FasterFlightConfig.get().showSpeedIndicator) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }

        // This runs on the render thread every frame while the indicator is visible. A cosmetic
        // overlay must never be able to take the game down, so any failure here is swallowed and
        // simply skips drawing for that frame rather than propagating into the render loop.
        try {
            String label = String.format(java.util.Locale.ROOT, "%.1fx", FasterFlightConfig.getMultiplier());

            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();
            int textWidth = client.textRenderer.getWidth(label);

            int x = (screenWidth - textWidth) / 2;
            int y = screenHeight - HOTBAR_CLEARANCE;

            DrawableHelper.drawTextWithShadow(
                    matrices, client.textRenderer, TextCompat.literal(label), x, y, TEXT_COLOR);
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
