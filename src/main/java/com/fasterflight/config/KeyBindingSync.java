package com.fasterflight.config;

import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.clothconfig2.api.Modifier;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Bridges Cloth's {@link ModifierKeyCode} data object to a real vanilla {@link KeyMapping}.
 *
 * <p>Cloth's own keybind row helper reaches into the private {@code KeyMapping} internals, which
 * would force the mod to ship an access widener. Going through {@link ModifierKeyCode} and the
 * public {@link KeyMapping#setKey} keeps the config screen and the vanilla Controls page pointed at
 * the same object, so the two can never disagree.
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
    static ModifierKeyCode read(KeyMapping binding) {
        return ModifierKeyCode.of(KeyMappingHelper.getBoundKeyOf(binding), Modifier.none());
    }

    /**
     * Writes a Cloth value back onto the vanilla keybind.
     *
     * <p>The {@link Modifier} is ignored: vanilla keybinds have no modifier concept, so {@code Ctrl+K}
     * is stored as the plain key. The Cloth row disables modifiers to match.
     *
     * <p>{@link KeyMapping#resetMapping()} is essential. {@code KeyMapping} keeps a static lookup
     * from key code back to the mappings using it, and that map is what drives
     * {@link KeyMapping#consumeClick()}. Changing only the bound key leaves the map pointing at the
     * old code, so the new key is never reported as pressed and the action silently stops firing.
     * The vanilla Controls screen refreshes the map after rebinding, which is why a key set there
     * keeps working while one set here did not.
     *
     * <p>Note this affects keybinds read through {@code consumeClick()}. Bindings polled from raw
     * input state (such as the hold-to-boost key) are unaffected either way, since they never
     * consult the map.
     *
     * @param binding the keybind to update; must not be {@code null}
     * @param value the new value chosen in the config screen
     */
    static void apply(KeyMapping binding, ModifierKeyCode value) {
        InputConstants.Key key = value.getKeyCode();
        if (key != null) {
            binding.setKey(key);
        }
        KeyMapping.resetMapping();
    }
}
