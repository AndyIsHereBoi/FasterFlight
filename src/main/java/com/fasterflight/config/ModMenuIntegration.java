package com.fasterflight.config;

import com.fasterflight.FasterFlightMod;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Optional;

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
                .setTitle(Component.translatable("fasterflight.config.title"))
                .setSavingRunnable(FasterFlightConfig::save);

        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("fasterflight.config.category.general"));

        ConfigEntryBuilder entries = builder.entryBuilder();

        MultiplierSliderEntry multiplierEntry = new MultiplierSliderEntry(
                Component.translatable("fasterflight.config.speedMultiplier"),
                config.speedMultiplier,
                FasterFlightConfig::setMultiplier
        );
        multiplierEntry.setTooltipSupplier(
                () -> queuedHint("fasterflight.config.speedMultiplier.tooltip"));
        general.addEntry(multiplierEntry);

        general.addEntry(new ManualEntryRow(
                Component.translatable("fasterflight.config.speedMultiplier.manual"),
                multiplierEntry.borrowFieldForSeparateRow(),
                multiplierEntry::resetToDefault,
                multiplierEntry));

        // These rows deliberately avoid Cloth's own keybind helper, which reflects into private
        // KeyMapping internals and would force an access widener. Going through ModifierKeyCode and
        // the public KeyMapping#setKey keeps the Controls page and this screen sharing one object.
        general.addEntry(entries.startModifierKeyCodeField(
                        Component.translatable("fasterflight.config.boostKey"),
                        KeyBindingSync.read(FasterFlightMod.getBoostKeyMapping()))
                .setAllowModifiers(false)
                .setModifierSaveConsumer(value ->
                        KeyBindingSync.apply(FasterFlightMod.getBoostKeyMapping(), value))
                .setTooltipSupplier(() -> queuedHint("fasterflight.config.boostKey.tooltip"))
                .build());

        general.addEntry(entries.startModifierKeyCodeField(
                        Component.translatable("fasterflight.config.openConfigKey"),
                        KeyBindingSync.read(FasterFlightMod.getOpenConfigKeyMapping()))
                .setAllowModifiers(false)
                .setModifierSaveConsumer(value ->
                        KeyBindingSync.apply(FasterFlightMod.getOpenConfigKeyMapping(), value))
                .setTooltipSupplier(() -> queuedHint("fasterflight.config.openConfigKey.tooltip"))
                .build());

        general.addEntry(entries.startBooleanToggle(
                        Component.translatable("fasterflight.config.showIndicator"),
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
    private static Optional<Component[]> queuedHint(String hintKey) {
        TooltipBar.publish(hintKey);
        return Optional.empty();
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

    private static boolean isSearchField(GuiEventListener element) {
        return element.getClass().getName().contains("SearchFieldEntry");
    }
}
