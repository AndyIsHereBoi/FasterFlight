package com.fasterflight.config;

import me.shedaniel.clothconfig2.api.Modifier;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * Bridges Cloth's {@link ModifierKeyCode} data object to a real vanilla {@link KeyBinding}.
 *
 * <p>Cloth's {@code fillKeybindingField} would be the obvious choice, but it reflects into the
 * private {@code KeyBinding#boundKey}, which forces the mod to ship an access widener. A widener is
 * compiled to per-version intermediary names, so it would pin the jar to one Minecraft version.
 *
 * <p>{@code startModifierKeyCodeField} works purely on {@link ModifierKeyCode}, and
 * {@link KeyBinding#setBoundKey} is public on every supported version, giving a reflection-free path.
 *
 * <p>Syncing both ways keeps the ModMenu row and the vanilla Controls page agreeing: {@link #apply}
 * on edit, {@link #read} when the screen is rebuilt.
 */
final class KeyBindingSync {

    private KeyBindingSync() {
    }

    /**
     * Reads the currently bound key of a vanilla keybind as a Cloth value.
     *
     * @param binding the keybind to read; must not be {@code null}
     * @return a {@link ModifierKeyCode} representing the bound key, with no modifier
     */
    static ModifierKeyCode read(KeyBinding binding) {
        return ModifierKeyCode.of(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
                .getBoundKeyOf(binding), Modifier.none());
    }

    /**
     * Writes a Cloth value back onto the vanilla keybind.
     *
     * <p>The {@link Modifier} is ignored: vanilla keybinds have no modifier concept, so {@code Ctrl+K}
     * is stored as the plain key. The Cloth row disables modifiers to match.
     *
     * <p>{@code updateKeysByCode()} is essential. {@code KeyBinding} keeps a static lookup from key
     * code back to the bindings using it, and that map is what drives {@code wasPressed()}. Changing
     * only the bound key leaves the map pointing at the old code, so the new key is never reported as
     * pressed and the action silently stops firing. The vanilla Controls screen refreshes the map
     * after rebinding, which is why a key set there keeps working while one set here did not.
     *
     * <p>Note this affects keybinds read through {@code wasPressed()}. Bindings polled from raw GLFW
     * state (such as the hold-to-boost key) are unaffected either way, since they never consult the
     * map.
     *
     * @param binding the keybind to update; must not be {@code null}
     * @param value the new value chosen in the config screen
     */
    static void apply(KeyBinding binding, ModifierKeyCode value) {
        InputUtil.Key key = value.getKeyCode();
        if (key != null) {
            binding.setBoundKey(key);
        }
        KeyBinding.updateKeysByCode();
    }
}
