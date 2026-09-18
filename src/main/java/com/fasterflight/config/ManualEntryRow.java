package com.fasterflight.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A Cloth Config row hosting a borrowed {@link TextFieldWidget} plus its own reset button, giving the
 * multiplier text box a line of its own beneath the slider.
 *
 * <p>The widget is reused rather than rebuilt, so this row and the slider share one value, caret and
 * focus state.
 */
public class ManualEntryRow extends AbstractConfigListEntry<Object> {

    private static final String HINT_KEY = "fasterflight.config.speedMultiplier.tooltip";
    private static final int WIDGET_HEIGHT = 20;

    private final TextFieldWidget field;
    private final ButtonWidget resetButton;

    /** Measured each frame so this row can copy the slider's exact left edge and width. */
    private final MultiplierSliderEntry sliderRow;

    public ManualEntryRow(Text fieldName, TextFieldWidget field, Runnable onReset,
                          MultiplierSliderEntry sliderRow) {
        super(fieldName, false);
        this.field = field;
        this.sliderRow = sliderRow;

        Text resetLabel = TextCompat.translatable("text.cloth-config.reset_value");

        // Constructed through ButtonCompat because 1.19.3 made the constructor protected and moved
        // callers onto a builder that does not exist on 1.18.2/1.19.2.
        this.resetButton = ButtonCompat.create(0, 0, 60, WIDGET_HEIGHT, resetLabel, onReset);

        // Adopt the row above's width so the two reset buttons line up exactly rather than relying on
        // the label measurement happening to agree with Cloth's own calculation.
        this.resetButton.setWidth(sliderRow.getResetButtonWidth());
    }

    @Override
    public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight,
                       int mouseX, int mouseY, boolean isHovered, float delta) {
        super.render(matrices, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);

        if (isHovered) {
            TooltipBar.publish(HINT_KEY);
        }

        // AbstractConfigListEntry paints only the hover highlight, so the label is drawn here.
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int labelY = y + (entryHeight - 8) / 2;
        font.drawWithShadow(matrices, getDisplayedFieldName(), x, labelY, getPreferredTextColor());

        int resetWidth = this.resetButton.getWidth();

        // Re-read every frame so the box tracks the slider when the window is resized, but only
        // write the values when they actually change. TextFieldWidget keeps private caret state
        // (firstCharacterIndex / selectionStart), and resizing re-clamps the visible text against
        // that state, so writing the width unconditionally every frame would fight the caret while
        // the player types and swallow freshly typed digits.
        int targetWidth = Math.max(20, this.sliderRow.getSliderWidth());
        if (this.field.getWidth() != targetWidth) {
            this.field.setWidth(targetWidth);
        }
        int targetX = this.sliderRow.getSliderLeft();
        int targetY = y + 1;
        int fieldX = WidgetCompat.getX(this.field);
        int fieldY = WidgetCompat.getY(this.field);
        if (fieldX == WidgetCompat.UNKNOWN || fieldY == WidgetCompat.UNKNOWN
                || fieldX != targetX || fieldY != targetY) {
            WidgetCompat.setPosition(this.field, targetX, targetY);
        }
        this.field.setEditable(isEditable());
        this.field.render(matrices, mouseX, mouseY, delta);

        WidgetCompat.setPosition(this.resetButton, x + entryWidth - resetWidth - 2, y);
        this.resetButton.active = isEditable();
        this.resetButton.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.resetButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return this.field.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Routes key presses to the borrowed text field.
     *
     * <p>Cloth never delivers {@code charTyped} to a list entry, so typing must be handled here.
     * Listing the field in {@code children()} is enough for mouse clicks but not for keyboard.
     *
     * <p>{@code MultiplierSliderEntry} forwards the same way for the inline layout.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.field.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.field.setTextFieldFocused(false);
                this.sliderRow.commitFieldFromCompanion();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.field.setTextFieldFocused(false);
                this.sliderRow.commitFieldFromCompanion();
                return true;
            }
            return this.field.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.field.isFocused()) {
            return this.field.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public Object getValue() {
        return null;
    }

    /**
     * Cloth 8.0.75 (MC 1.19.0) declares this abstract; 8.3.134 gave it a default body, so
     * implementing it is required for 1.19.0 and harmless on newer Cloth. Calling {@code super.save()}
     * is not an option: it does not exist on 8.0.75.
     *
     * <p>This row owns no value of its own, so there is nothing to persist.
     */
    @Override
    public void save() {
        // Value ownership belongs to the borrowed slider row; see commitIfFocusJustLost there.
    }

    @Override
    public Optional<Object> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public List<? extends Element> children() {
        List<Element> children = new ArrayList<>();
        children.add(this.field);
        children.add(this.resetButton);
        return children;
    }

    @Override
    public List<? extends Selectable> narratables() {
        List<Selectable> narratables = new ArrayList<>();
        narratables.add(this.field);
        narratables.add(this.resetButton);
        return narratables;
    }
}
