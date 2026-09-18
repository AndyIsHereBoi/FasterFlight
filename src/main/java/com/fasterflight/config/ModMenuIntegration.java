package com.fasterflight.config;

import com.fasterflight.FasterFlightMod;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Provides the ModMenu "Configure" screen, and builds the same screen for the in-game keybind.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuIntegration::createScreen;
    }

    /**
     * @param parent the screen to return to when this one closes, may be null
     * @return the built settings screen
     */
    public static Screen createScreen(Screen parent) {
        FasterFlightConfig config = FasterFlightConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(TextCompat.translatable("fasterflight.config.title"))
                .setSavingRunnable(FasterFlightConfig::save);

        ConfigCategory general = builder.getOrCreateCategory(
                TextCompat.translatable("fasterflight.config.category.general"));

        ConfigEntryBuilder entries = builder.entryBuilder();

        MultiplierSliderEntry multiplierEntry = new MultiplierSliderEntry(
                TextCompat.translatable("fasterflight.config.speedMultiplier"),
                config.speedMultiplier,
                FasterFlightConfig::setMultiplier
        );
        multiplierEntry.setTooltipSupplier(
                () -> queuedHint("fasterflight.config.speedMultiplier.tooltip"));
        general.addEntry(multiplierEntry);

        general.addEntry(new ManualEntryRow(
                TextCompat.translatable("fasterflight.config.speedMultiplier.manual"),
                multiplierEntry.borrowFieldForSeparateRow(),
                multiplierEntry::resetToDefault,
                multiplierEntry));

        // These rows deliberately do NOT use fillKeybindingField: that helper reflects into the
        // private KeyBinding#boundKey field, which forces the mod to ship an access widener. An
        // access widener is compiled to per-version intermediary names, so it would pin the jar to
        // a single Minecraft version. Cloth's ModifierKeyCode field needs no reflection, and the
        // value is mirrored onto the real keybind through the public KeyBinding#setBoundKey, so the
        // Controls page and this screen stay in sync while one jar stays valid for 1.18+.
        general.addEntry(entries.startModifierKeyCodeField(
                        TextCompat.translatable("fasterflight.config.boostKey"),
                        KeyBindingSync.read(FasterFlightMod.getBoostKeyBinding()))
                .setAllowModifiers(false)
                .setModifierSaveConsumer(value ->
                        KeyBindingSync.apply(FasterFlightMod.getBoostKeyBinding(), value))
                .setTooltipSupplier(() -> queuedHint("fasterflight.config.boostKey.tooltip"))
                .build());

        general.addEntry(entries.startModifierKeyCodeField(
                        TextCompat.translatable("fasterflight.config.openConfigKey"),
                        KeyBindingSync.read(FasterFlightMod.getOpenConfigKeyBinding()))
                .setAllowModifiers(false)
                .setModifierSaveConsumer(value ->
                        KeyBindingSync.apply(FasterFlightMod.getOpenConfigKeyBinding(), value))
                .setTooltipSupplier(() -> queuedHint("fasterflight.config.openConfigKey.tooltip"))
                .build());

        general.addEntry(entries.startBooleanToggle(
                        TextCompat.translatable("fasterflight.config.showIndicator"),
                        config.showSpeedIndicator)
                .setDefaultValue(true)
                .setTooltipSupplier(() -> queuedHint("fasterflight.config.showIndicator.tooltip"))
                .setSaveConsumer(newValue -> config.showSpeedIndicator = newValue)
                .build());

        removeSearchField(builder);

        return builder.build();
    }

    /**
     * Queues the hint for the bottom bar and returns nothing, so Cloth draws no floating tooltip.
     */
    private static java.util.Optional<Text[]> queuedHint(String hintKey) {
        TooltipBar.publish(hintKey);
        return java.util.Optional.empty();
    }

    /**
     * The builder offers no public way to hide the search box, so its row is removed from the screen
     * list directly. The list is rebuilt on every {@code init()}, including window resizes, so this
     * runs each time rather than once.
     */
    private static void removeSearchField(ConfigBuilder builder) {
        builder.setAfterInitConsumer(screen -> {
            if (!(screen instanceof ClothConfigScreen configScreen) || configScreen.listWidget == null) {
                return;
            }
            configScreen.listWidget.children().removeIf(ModMenuIntegration::isSearchField);
        });
    }

    private static boolean isSearchField(Element element) {
        return element.getClass().getName().contains("SearchFieldEntry");
    }
}
