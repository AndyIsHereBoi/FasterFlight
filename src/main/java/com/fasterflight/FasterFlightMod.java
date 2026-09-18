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
 * <p>The keybinds are ordinary vanilla {@link KeyBinding} instances so they appear on the Controls
 * page; the ModMenu screen edits those same instances rather than keeping its own copies.
 */
public class FasterFlightMod implements ClientModInitializer {

    public static final String KEY_BOOST = "key.fasterflight.boost";
    public static final String KEY_OPEN_CONFIG = "key.fasterflight.openConfig";
    public static final String KEY_CATEGORY = "key.categories.fasterflight";

    private static KeyBinding boostKeyBinding;
    private static KeyBinding openConfigKeyBinding;

    public static KeyBinding getBoostKeyBinding() {
        return boostKeyBinding;
    }

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

        // GLFW_KEY_UNKNOWN leaves this unassigned, so it cannot clash with another mod by default.
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
     * Opening the screen is a discrete action, so consuming the press event is correct here, unlike
     * the hold-to-boost key which polls raw state instead.
     */
    private static void handleOpenConfigKey(MinecraftClient client) {
        if (openConfigKeyBinding == null) {
            return;
        }

        // Consume the press even when a screen is already open, so it cannot fire once that closes.
        boolean pressed = openConfigKeyBinding.wasPressed();
        if (!pressed || client.currentScreen != null) {
            return;
        }

        client.setScreen(ModMenuIntegration.createScreen(null));
    }

    /**
     * Polls raw GLFW state rather than {@link KeyBinding#wasPressed()} because this key is held, and
     * consuming the press/release transitions would make the key unusable to anything else reading
     * them.
     */
    private static boolean isBoostKeyHeld(MinecraftClient client) {
        if (boostKeyBinding == null || client.getWindow() == null) {
            return false;
        }

        InputUtil.Key boundKey = KeyBindingHelper.getBoundKeyOf(boostKeyBinding);
        if (boundKey == InputUtil.UNKNOWN_KEY) {
            return false;
        }

        // A key rebound to a mouse button has no key code, so it is ignored rather than misread.
        if (boundKey.getCategory() != InputUtil.Type.KEYSYM) {
            return false;
        }

        return InputUtil.isKeyPressed(client.getWindow().getHandle(), boundKey.getCode());
    }
}
