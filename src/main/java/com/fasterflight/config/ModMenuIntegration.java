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
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

/**
 * Provides the ModMenu "Configure" screen.
 *
 * <p>The screen is built with Cloth Config rather than Autoconfig's generated GUI so the multiplier
 * can use a slider with a paired manual-entry box, and so the keybind row can edit the same
 * {@link KeyBinding} instance that the vanilla Controls page edits. That shared instance is what
 * keeps the two views synchronised: whichever one writes to it, the other reads the new value the
 * next time it is opened.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuIntegration::createScreen;
    }

    /**
     * Builds the FasterFlight settings screen.
     *
     * <p>Exposed as a static method so it can also be opened from the in-game "open config" keybind,
     * which has no ModMenu involved. Passing the current screen as {@code parent} makes the usual
     * Cancel and Save &amp; Quit buttons return to wherever the player came from.
     *
     * @param parent the screen to return to when this one closes, may be null
     * @return the built settings screen
     */
    public static Screen createScreen(Screen parent) {
        FasterFlightConfig config = FasterFlightConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("fasterflight.config.title"))
                .setSavingRunnable(FasterFlightConfig::save);

        ConfigCategory general = builder.getOrCreateCategory(
                Text.translatable("fasterflight.config.category.general"));

        ConfigEntryBuilder entries = builder.entryBuilder();

        MultiplierSliderEntry multiplierEntry = new MultiplierSliderEntry(
                Text.translatable("fasterflight.config.speedMultiplier"),
                config.speedMultiplier,
                FasterFlightConfig::setMultiplier
        );
        // Hints are shown in a bar at the bottom of the screen instead of as floating boxes that
        // cover the controls, so Cloth's floating tooltip is suppressed and the text queued for the
        // bar instead.
        multiplierEntry.setTooltipSupplier(
                () -> queuedHint("fasterflight.config.speedMultiplier.tooltip"));
        general.addEntry(multiplierEntry);

        // Give the manual text box its own row beneath the slider, with its own reset button.
        // The same widget instance is moved onto the new row, so both stay in step and only one
        // of them owns the caret. Its label repeats the row above with "Manual" appended.
        general.addEntry(new ManualEntryRow(
                Text.translatable("fasterflight.config.speedMultiplier.manual"),
                multiplierEntry.borrowFieldForSeparateRow(),
                multiplierEntry::resetToDefault,
                multiplierEntry));

        // fillKeybindingField binds directly to the shared KeyBinding, so edits made here are
        // visible on the vanilla Controls page and vice versa.
        var keyBindEntry = entries.fillKeybindingField(
                        Text.translatable("fasterflight.config.boostKey"),
                        FasterFlightMod.getBoostKeyBinding())
                .setTooltipSupplier(() -> queuedHint("fasterflight.config.boostKey.tooltip"))
                .build();
        general.addEntry(keyBindEntry);

        var openConfigEntry = entries.fillKeybindingField(
                        Text.translatable("fasterflight.config.openConfigKey"),
                        FasterFlightMod.getOpenConfigKeyBinding())
                .setTooltipSupplier(() -> queuedHint("fasterflight.config.openConfigKey.tooltip"))
                .build();
        general.addEntry(openConfigEntry);

        var indicatorEntry = entries.startBooleanToggle(
                        Text.translatable("fasterflight.config.showIndicator"),
                        config.showSpeedIndicator)
                .setDefaultValue(true)
                .setTooltipSupplier(() -> queuedHint("fasterflight.config.showIndicator.tooltip"))
                .setSaveConsumer(newValue -> config.showSpeedIndicator = newValue)
                .build();
        general.addEntry(indicatorEntry);

        // Cloth Config has no public option for hiding the search box, and it is dead weight on a
        // screen with only four entries. It is created as a list row, so it can be removed from
        // the built screen's selector the same way any other row would be.
        removeSearchField(builder);

        return builder.build();
    }

    /**
     * Clears Cloth's floating tooltip and queues the hint for the bottom bar instead.
     *
     * <p>Cloth calls the supplier on every frame the row is hovered, so returning empty here and
     * queueing separately is what moves the text out of the floating box and into the bottom bar.
     *
     * @param hintKey translation key of the hint text
     * @return always empty, so no floating tooltip is drawn
     */
    private static java.util.Optional<Text[]> queuedHint(String hintKey) {
        TooltipBar.publish(hintKey);
        return java.util.Optional.empty();
    }

    /**
     * Strips Cloth Config's search box from the config screen.
     *
     * <p>The builder offers no public way to hide the search box, and it is dead weight on a screen
     * with only four entries. The box is a normal row inside the screen's list widget, so it can be
     * removed from that list directly.
     *
     * <p>The list is rebuilt on every {@code init()}, which includes window resizes, so the row is
     * removed each time rather than once. If a future Cloth Config version renames the widget type,
     * nothing is removed and the search box simply stays visible.
     *
     * @param builder the screen builder to adjust
     */
    private static void removeSearchField(ConfigBuilder builder) {
        builder.setAfterInitConsumer(screen -> {
            if (!(screen instanceof ClothConfigScreen configScreen) || configScreen.listWidget == null) {
                return;
            }
            configScreen.listWidget.children().removeIf(ModMenuIntegration::isSearchField);
        });
    }

    /**
     * @param element a row from the config screen
     * @return whether the row is Cloth Config's search box
     */
    private static boolean isSearchField(Element element) {
        return element.getClass().getName().contains("SearchFieldEntry");
    }
}
