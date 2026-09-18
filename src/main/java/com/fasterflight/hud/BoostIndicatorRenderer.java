package com.fasterflight.hud;

import com.fasterflight.FlightSpeedController;
import com.fasterflight.config.FasterFlightConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/**
 * Draws a small multiplier readout just above the hotbar while the boost is active.
 *
 * <p>The text is only shown when the boost is actually being applied, so it doubles as confirmation
 * that the keybind is bound and working.
 */
public final class BoostIndicatorRenderer {

    /** Vertical offset above the bottom of the screen, chosen to clear the hotbar and its tooltips. */
    private static final int HOTBAR_CLEARANCE = 55;

    /** ARGB colour of the readout; a light amber that stays readable over grass and sky alike. */
    private static final int TEXT_COLOR = 0xFFFFC24A;

    /** ARGB colour of the drop shadow drawn behind the text. */
    private static final int SHADOW_COLOR = 0x80000000;

    private BoostIndicatorRenderer() {
    }

    /** Registers the HUD callback. Called once from the mod initializer. */
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

        // Offset a one pixel shadow manually so the text keeps contrast against bright terrain.
        DrawableHelper.drawTextWithShadow(matrices, client.textRenderer, Text.literal(label), x, y, TEXT_COLOR);
    }
}
