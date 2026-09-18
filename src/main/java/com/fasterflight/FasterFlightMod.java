package com.fasterflight;

import com.fasterflight.config.FasterFlightConfig;
import com.fasterflight.config.ModMenuIntegration;
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
 * <p>Registers the hold-to-boost keybind, the keybind that opens the settings screen, and drives the
 * per-tick speed application. Because the keybinds are normal vanilla {@link KeyBinding} instances,
 * they appear on the Controls page and can be changed either there or from the ModMenu screen, with
 * both views reading the same instance.
 */
public class FasterFlightMod implements ClientModInitializer {

    /** Translation key for the keybind label. */
    public static final String KEY_BOOST = "key.fasterflight.boost";

    /** Translation key for the keybind that opens the settings screen. */
    public static final String KEY_OPEN_CONFIG = "key.fasterflight.openConfig";

    /** Translation key for the keybind category. */
    public static final String KEY_CATEGORY = "key.categories.fasterflight";

    /**
     * The single shared boost keybind instance. Any screen that edits the key must go through
     * {@link KeyBindingHelper#getBoundKeyOf(KeyBinding)} and {@link KeyBinding#setBoundKey} on this
     * object so that the Controls page and ModMenu always agree.
     */
    private static KeyBinding boostKeyBinding;

    /** The shared keybind instance that opens the settings screen. Unbound by default. */
    private static KeyBinding openConfigKeyBinding;

    /** @return the shared boost keybind instance. */
    public static KeyBinding getBoostKeyBinding() {
        return boostKeyBinding;
    }

    /** @return the shared keybind instance that opens the settings screen. */
    public static KeyBinding getOpenConfigKeyBinding() {
        return openConfigKeyBinding;
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

        // Unbound by default: GLFW_KEY_UNKNOWN means the player has to assign it themselves, which
        // avoids clashing with another mod's key out of the box.
        openConfigKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEY_OPEN_CONFIG,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY
        ));

        BoostIndicatorRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(FasterFlightMod::onEndTick);
    }

    private static void onEndTick(MinecraftClient client) {
        FlightSpeedController.tick(client, isBoostKeyHeld(client));
        handleOpenConfigKey(client);
    }

    /**
     * Opens the settings screen when the open-config key is pressed.
     *
     * <p>Uses {@link KeyBinding#wasPressed()} because this is a discrete action rather than a held
     * one, so consuming the press event is exactly what is wanted here. The key is only acted on when
     * no other screen is open, so it cannot stack screens or fire while the player is typing.
     *
     * @param client the Minecraft client instance
     */
    private static void handleOpenConfigKey(MinecraftClient client) {
        if (openConfigKeyBinding == null) {
            return;
        }

        // Drain pressed events even when a screen is open, so a queued press cannot fire later.
        boolean pressed = openConfigKeyBinding.wasPressed();
        if (!pressed || client.currentScreen != null) {
            return;
        }

        client.setScreen(ModMenuIntegration.createScreen(null));
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
