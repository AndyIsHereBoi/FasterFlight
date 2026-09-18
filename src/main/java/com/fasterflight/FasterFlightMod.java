package com.fasterflight;

import com.fasterflight.config.FasterFlightConfig;
import com.fasterflight.hud.BoostIndicatorRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint for FasterFlight.
 *
 * <p>Registers the hold-to-boost keybind and drives the per-tick speed application. Because the
 * keybind is a normal vanilla {@link KeyBinding}, it appears on the Controls page and can be
 * changed either there or from the ModMenu screen, with both views reading the same instance.
 */
public class FasterFlightMod implements ClientModInitializer {

    /** Translation key for the keybind label. */
    public static final String KEY_BOOST = "key.fasterflight.boost";

    /** Translation key for the keybind category. */
    public static final String KEY_CATEGORY = "key.categories.fasterflight";

    /**
     * The single shared keybind instance. Any screen that edits the key must go through
     * {@link KeyBindingHelper#getBoundKeyOf(KeyBinding)} and {@link KeyBinding#setBoundKey} on this
     * object so that the Controls page and ModMenu always agree.
     */
    private static KeyBinding boostKeyBinding;

    /** @return the shared boost keybind instance. */
    public static KeyBinding getBoostKeyBinding() {
        return boostKeyBinding;
    }

    @Override
    public void onInitializeClient() {
        FasterFlightConfig.register();

        boostKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEY_BOOST,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                KEY_CATEGORY
        ));

        BoostIndicatorRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(FasterFlightMod::onEndTick);
    }

    private static void onEndTick(MinecraftClient client) {
        FlightSpeedController.tick(client, isBoostKeyHeld(client));
    }

    /**
     * Tests the raw GLFW key state rather than {@link KeyBinding#wasPressed()}.
     *
     * <p>The keybind is a hold-to-activate control, and polling the raw state avoids consuming the
     * vanilla pressed/released transition events, which would otherwise make the key unusable for
     * anything that reads them.
     *
     * @param client the Minecraft client instance
     * @return whether the bound boost key is currently down
     */
    private static boolean isBoostKeyHeld(MinecraftClient client) {
        if (boostKeyBinding == null || client.getWindow() == null) {
            return false;
        }

        InputUtil.Key boundKey = KeyBindingHelper.getBoundKeyOf(boostKeyBinding);
        if (boundKey == InputUtil.UNKNOWN_KEY) {
            return false;
        }

        // A rebound key of type MOUSE is not a key code, so it is ignored rather than misread.
        if (boundKey.getCategory() != InputUtil.Type.KEYSYM) {
            return false;
        }

        return InputUtil.isKeyPressed(client.getWindow().getHandle(), boundKey.getCode());
    }
}
