package com.fasterflight;

import com.fasterflight.config.FasterFlightConfig;
import com.fasterflight.config.ModMenuIntegration;
import com.fasterflight.hud.BoostIndicatorRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint for FasterFlight.
 *
 * <p>The keybinds are ordinary vanilla {@link KeyMapping} instances so they appear on the Controls
 * page; the ModMenu screen edits those same instances rather than keeping its own copies.
 */
public class FasterFlightMod implements ClientModInitializer {

    public static final String KEY_BOOST = "key.fasterflight.boost";
    public static final String KEY_OPEN_CONFIG = "key.fasterflight.openConfig";

    /**
     * Translation key for the custom keybind category.
     *
     * <p>{@code KeyMapping.Category#label()} is implemented as
     * {@code Component.translatable(id.toLanguageKey("key.category"))}, so this must match the
     * category id below, not the old flat {@code key.categories.*} form.
     */
    public static final String KEY_CATEGORY_LABEL = "key.category.fasterflight.keys";

    private static final Identifier CATEGORY_ID =
            Identifier.fromNamespaceAndPath("fasterflight", "keys");

    /**
     * Registers a dedicated category so the two keybinds are grouped under "FasterFlight" on the
     * Controls page rather than being buried in Miscellaneous.
     */
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CATEGORY_ID);

    private static KeyMapping boostKeyMapping;
    private static KeyMapping openConfigKeyMapping;

    public static KeyMapping getBoostKeyMapping() {
        return boostKeyMapping;
    }

    public static KeyMapping getOpenConfigKeyMapping() {
        return openConfigKeyMapping;
    }

    @Override
    public void onInitializeClient() {
        FasterFlightConfig.register();

        boostKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                KEY_BOOST,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                CATEGORY
        ));

        // GLFW_KEY_UNKNOWN leaves this unassigned, so it cannot clash with another mod by default.
        openConfigKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                KEY_OPEN_CONFIG,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY
        ));

        BoostIndicatorRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(FasterFlightMod::onEndTick);
    }

    private static void onEndTick(Minecraft client) {
        FlightSpeedController.tick(client, isBoostKeyHeld(client));
        handleOpenConfigKey(client);
    }

    /**
     * Opening the screen is a discrete action, so consuming the click is correct here, unlike the
     * hold-to-boost key which polls raw state instead.
     */
    private static void handleOpenConfigKey(Minecraft client) {
        if (openConfigKeyMapping == null) {
            return;
        }

        // Consume the press even when a screen is already open, so it cannot fire once that closes.
        boolean pressed = openConfigKeyMapping.consumeClick();
        if (!pressed || client.screen != null) {
            return;
        }

        client.setScreen(ModMenuIntegration.createScreen(null));
    }

    /**
     * Polls raw input state rather than {@link KeyMapping#consumeClick()} because this key is held,
     * and consuming the press/release transitions would make the key unusable to anything else
     * reading them.
     */
    private static boolean isBoostKeyHeld(Minecraft client) {
        if (boostKeyMapping == null || client.getWindow() == null) {
            return false;
        }

        InputConstants.Key boundKey = KeyMappingHelper.getBoundKeyOf(boostKeyMapping);
        if (boundKey == InputConstants.UNKNOWN) {
            return false;
        }

        // A key rebound to a mouse button has no key code, so it is ignored rather than misread.
        if (boundKey.getType() != InputConstants.Type.KEYSYM) {
            return false;
        }

        return InputConstants.isKeyDown(client.getWindow(), boundKey.getValue());
    }
}
