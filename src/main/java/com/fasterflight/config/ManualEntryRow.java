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
 * A Cloth Config row that hosts a pre-existing {@link TextFieldWidget} plus its own reset button.
 *
 * <p>Gives the multiplier text box a line of its own beneath the slider. The widget is borrowed
 * rather than rebuilt, so this row and the slider always show the same value and share one caret,
 * selection and focus state.
 *
 * <p>The layout mirrors a standard Cloth Config slider row: the field name is drawn on the left and
 * the control stretches to the right edge, with a reset button at the far right.
 */
public class ManualEntryRow extends AbstractConfigListEntry<Object> {

    /** Hint shown in the bottom bar while this row is hovered. */
    private static final String HINT_KEY = "fasterflight.config.speedMultiplier.tooltip";

    /** Matches the height of a standard Cloth Config option row. */
    private static final int WIDGET_HEIGHT = 20;

    private final TextFieldWidget field;
    private final ButtonWidget resetButton;

    /**
     * The slider row this entry sits beneath.
     *
     * <p>Its slider widget is measured each frame so this row's controls can copy the slider's exact
     * left edge and width. Reading the real geometry avoids hard-coding Cloth's internal layout
     * constants, which would drift if they ever changed.
     */
    private final MultiplierSliderEntry sliderRow;

    /**
     * @param fieldName the label shown to the left of the row
     * @param field     the text box to host; ownership stays with whoever created it
     * @param onReset   invoked when the reset button is pressed
     * @param sliderRow the slider row above, whose control geometry this row matches
     */
    public ManualEntryRow(Text fieldName, TextFieldWidget field, Runnable onReset,
                          MultiplierSliderEntry sliderRow) {
        super(fieldName, false);
        this.field = field;
        this.sliderRow = sliderRow;

        // Cloth Config sizes its reset buttons to the label width plus six pixels of padding, so the
        // same formula is used here to make this button match the ones on the rows above.
        Text resetLabel = Text.translatable("text.cloth-config.reset_value");
        int resetWidth = MinecraftClient.getInstance().textRenderer.getWidth(resetLabel) + 6;

        // ButtonWidget has no builder factory in 1.19.2, so it is constructed directly.
        this.resetButton = new ButtonWidget(
                0, 0, resetWidth, WIDGET_HEIGHT,
                resetLabel,
                button -> onReset.run());

        // Adopt the row above's reset button width so the two buttons line up exactly, rather than
        // relying on the label measurement happening to agree with Cloth's own calculation.
        this.resetButton.setWidth(sliderRow.getResetButtonWidth());
    }

    /** @return this row's hosted text box, so the owning screen can drive focus and commits. */
    public TextFieldWidget getField() {
        return this.field;
    }

    @Override
    public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight,
                       int mouseX, int mouseY, boolean isHovered, float delta) {
        super.render(matrices, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);

        // Describe this row in the bottom hint bar rather than in a floating box over the page.
        if (isHovered) {
            TooltipBar.publish(HINT_KEY);
        }

        // AbstractConfigListEntry only paints the hover highlight; concrete entries are responsible
        // for drawing their own label, so it has to be done here or the row appears unlabelled.
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        Text label = getDisplayedFieldName();
        int labelY = y + (entryHeight - 8) / 2;
        font.drawWithShadow(matrices, label, x, labelY, getPreferredTextColor());

        int resetWidth = this.resetButton.getWidth();

        // Copy the slider's exact left edge and width so the text box spans precisely the same span
        // the slider does. These are read from the laid-out slider widget each frame, so the box
        // tracks the slider correctly at any window size rather than being recomputed from
        // assumptions about Cloth's internal column widths.
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
