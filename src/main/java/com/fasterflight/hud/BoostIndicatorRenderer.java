package com.fasterflight.hud;

import com.fasterflight.FlightSpeedController;
import com.fasterflight.config.FasterFlightConfig;
import com.fasterflight.config.TextCompat;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Draws the active multiplier just above the hotbar while the boost is being applied.
 */
public final class BoostIndicatorRenderer {

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

        String label = String.format(java.util.Locale.ROOT, "%.1fx", FasterFlightConfig.getMultiplier());

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int textWidth = client.textRenderer.getWidth(label);

        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - HOTBAR_CLEARANCE;

        DrawableHelper.drawTextWithShadow(matrices, client.textRenderer, TextCompat.literal(label), x, y, TEXT_COLOR);
    }
}
