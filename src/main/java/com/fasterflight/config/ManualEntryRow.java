package com.fasterflight.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A Cloth Config row hosting a borrowed {@link EditBox} plus its own reset button, giving the
 * multiplier text box a line of its own beneath the slider.
 *
 * <p>The widget is reused rather than rebuilt, so this row and the slider share one value, caret and
 * focus state.
 */
public class ManualEntryRow extends AbstractConfigListEntry<Object> {

    private static final String HINT_KEY = "fasterflight.config.speedMultiplier.tooltip";
    private static final int WIDGET_HEIGHT = 20;

    private final EditBox field;
    private final Button resetButton;

    /** Measured each frame so this row can copy the slider's exact left edge and width. */
    private final MultiplierSliderEntry sliderRow;

    public ManualEntryRow(Component fieldName, EditBox field, Runnable onReset,
                          MultiplierSliderEntry sliderRow) {
        super(fieldName, false);
        this.field = field;
        this.sliderRow = sliderRow;

        // Built through the builder because Button's constructor is protected on this version.
        this.resetButton = Button.builder(
                        Component.translatable("text.cloth-config.reset_value"),
                        button -> onReset.run())
                .bounds(0, 0, 60, WIDGET_HEIGHT)
                .build();

        // Adopt the row above's width so the two reset buttons line up exactly rather than relying on
        // the label measurement happening to agree with Cloth's own calculation.
        this.resetButton.setWidth(sliderRow.getResetButtonWidth());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int index, int y, int x, int entryWidth,
                                   int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        super.extractRenderState(extractor, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);

        if (isHovered) {
            TooltipBar.publish(HINT_KEY);
        }

        // The superclass paints only the hover highlight, so the label is drawn here.
        Font font = Minecraft.getInstance().font;
        int labelY = y + (entryHeight - 8) / 2;
        extractor.text(font, getDisplayedFieldName(), x, labelY, getPreferredTextColor(), true);

        int resetWidth = this.resetButton.getWidth();

        // Re-read every frame so the box tracks the slider when the window is resized, but only
        // write the width when it actually changes. EditBox keeps private caret state, and resizing
        // re-clamps the visible text against that state, so writing the width unconditionally every
        // frame would fight the caret while the player types and swallow freshly typed digits.
        int targetWidth = Math.max(20, this.sliderRow.getSliderWidth());
        if (this.field.getWidth() != targetWidth) {
            this.field.setWidth(targetWidth);
        }
        this.field.setX(this.sliderRow.getSliderLeft());
        this.field.setY(y + 1);
        this.field.setEditable(isEditable());
        this.field.extractRenderState(extractor, mouseX, mouseY, delta);

        this.resetButton.setX(x + entryWidth - resetWidth - 2);
        this.resetButton.setY(y);
        this.resetButton.active = isEditable();
        this.resetButton.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.resetButton.mouseClicked(event, doubleClick)) {
            return true;
        }
        return this.field.mouseClicked(event, doubleClick);
    }

    /**
     * Routes key presses to the borrowed text field.
     *
     * <p>Cloth never delivers typed characters to a list entry, so typing must be handled here.
     * Listing the field in {@code children()} is enough for mouse clicks but not for keyboard.
     *
     * <p>{@code MultiplierSliderEntry} forwards the same way for the inline layout.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.field.isFocused()) {
            int keyCode = event.key();
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.field.setFocused(false);
                this.sliderRow.commitFieldFromCompanion();
                return true;
            }
            return this.field.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.field.isFocused()) {
            return this.field.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public Object getValue() {
        return null;
    }

    /**
     * This row owns no value of its own: the borrowed slider row holds the real value and persists
     * it via {@code commitField}, so there is nothing to save here.
     */
    @Override
    public void save() {
    }

    @Override
    public Optional<Object> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        List<GuiEventListener> children = new ArrayList<>();
        children.add(this.field);
        children.add(this.resetButton);
        return children;
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        List<NarratableEntry> narratables = new ArrayList<>();
        narratables.add(this.field);
        narratables.add(this.resetButton);
        return narratables;
    }
}
