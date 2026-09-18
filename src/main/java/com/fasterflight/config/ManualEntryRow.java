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

        Text resetLabel = Text.translatable("text.cloth-config.reset_value");

        // ButtonWidget has no builder factory in 1.19.2, so it is constructed directly.
        this.resetButton = new ButtonWidget(
                0, 0, 60, WIDGET_HEIGHT,
                resetLabel,
                button -> onReset.run());

        // Adopt the row above's width so the two reset buttons line up exactly rather than relying on
        // the label measurement happening to agree with Cloth's own calculation.
        this.resetButton.setWidth(sliderRow.getResetButtonWidth());
    }

    public TextFieldWidget getField() {
        return this.field;
    }

    @Override
    public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight,
                       int mouseX, int mouseY, boolean isHovered, float delta) {
        super.render(matrices, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);

        if (isHovered) {
            TooltipBar.publish(HINT_KEY);
        }

        // AbstractConfigListEntry only paints the hover highlight, so the label is drawn here.
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int labelY = y + (entryHeight - 8) / 2;
        font.drawWithShadow(matrices, getDisplayedFieldName(), x, labelY, getPreferredTextColor());

        int resetWidth = this.resetButton.getWidth();

        // Re-read every frame so the box tracks the slider when the window is resized.
        this.field.setWidth(Math.max(20, this.sliderRow.getSliderWidth()));
        this.field.x = this.sliderRow.getSliderLeft();
        this.field.y = y + 1;
        this.field.setEditable(isEditable());
        this.field.render(matrices, mouseX, mouseY, delta);

        this.resetButton.x = x + entryWidth - resetWidth - 2;
        this.resetButton.y = y;
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

    @Override
    public Object getValue() {
        return null;
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
