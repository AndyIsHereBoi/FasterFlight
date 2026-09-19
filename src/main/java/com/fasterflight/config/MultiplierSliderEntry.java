package com.fasterflight.config;

import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A Cloth Config entry pairing an integer slider with a manual-entry text box, which Cloth has no
 * built-in equivalent for. The two stay in step: dragging the slider reformats the box, and
 * committing the box (Enter, or clicking away) moves the slider and persists the value.
 *
 * <p>Values are stored as whole slider steps divided by {@link #SLIDER_SCALE}, giving the required
 * 0.1 resolution.
 *
 * <p>The text box can be handed to another row via {@link #borrowFieldForSeparateRow()}, after which
 * this entry stops drawing and hit-testing it.
 */
public class MultiplierSliderEntry extends IntegerSliderEntry {

    /** Slider steps per 1.0x of multiplier. */
    public static final int SLIDER_SCALE = 10;

    private static final int SLIDER_WIDTH = 118;
    private static final int FIELD_WIDTH = 42;
    private static final int WIDGET_HEIGHT = 20;
    private static final int WIDGET_GAP = 4;

    private final EditBox multiplierField;

    private int sliderLeft = 0;
    private int sliderWidth = SLIDER_WIDTH;

    private boolean fieldWasFocused = false;

    /** Set while the text box is drawn by another row, which then owns it. */
    private boolean fieldInSeparateRow = false;

    public MultiplierSliderEntry(Component fieldName, double value, Consumer<Double> onValueChanged) {
        super(
                fieldName,
                toSlider(FasterFlightConfig.MIN_MULTIPLIER),
                toSlider(FasterFlightConfig.MAX_MULTIPLIER),
                toSlider(value),
                Component.translatable("text.cloth-config.reset_value"),
                () -> toSlider(1.0D),
                sliderValue -> onValueChanged.accept(fromSlider(sliderValue))
        );

        setTextGetter(sliderValue -> MultiplierFormat.toComponent(fromSlider(sliderValue)));

        this.multiplierField = new EditBox(
                MultiplierFormat.font(),
                0, 0, FIELD_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fasterflight.config.speedMultiplier")
        );
        this.multiplierField.setMaxLength(6);
        this.multiplierField.setValue(MultiplierFormat.format(value));
        // No change listener on purpose: reacting to each keystroke would rewrite the box mid-edit,
        // making it impossible to delete a digit because the rewritten text puts it straight back.
        //
        // This version of EditBox has no text-filter API at all (setTextPredicate/setFilter are
        // both gone), so illegal characters are no longer rejected as they are typed. That is safe
        // because commitField() validates and reverts anything that does not parse.
    }

    /**
     * Records the slider's on-screen box for the row beneath to match.
     *
     * <p>Reading the laid-out widget is necessary because Cloth sizes the label column from the
     * longest field name rather than a constant, so recomputing it would drift as the window
     * resizes. The widget's position is public on this version, so no reflection is involved.
     */
    private void captureSliderGeometry() {
        // The slider's concrete type is package-private inside Cloth, so it is read through the
        // public AbstractWidget supertype. Its geometry accessors are public on this version, which
        // is why no reflection is needed here any more.
        AbstractWidget slider = (AbstractWidget) this.sliderWidget;
        this.sliderLeft = slider.getX();
        this.sliderWidth = Math.max(SLIDER_WIDTH, slider.getWidth());
    }

    /** @return the slider's left edge as recorded during this row's last render. */
    public int getSliderLeft() {
        return this.sliderLeft;
    }

    /** @return the slider's width as recorded during this row's last render. */
    public int getSliderWidth() {
        return this.sliderWidth;
    }

    /** @return this row's reset button width, so a companion row can match it. */
    public int getResetButtonWidth() {
        return this.resetButton != null ? this.resetButton.getWidth() : 60;
    }

    /**
     * Hands the text box to another row so it can appear on its own line. The same instance is
     * returned rather than a copy, so both rows share one value, caret and focus state.
     */
    public EditBox borrowFieldForSeparateRow() {
        this.fieldInSeparateRow = true;
        return this.multiplierField;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int index, int y, int x, int entryWidth,
                                   int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        super.extractRenderState(extractor, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);

        captureSliderGeometry();

        commitIfFocusJustLost();

        // Mirror the slider into the box while it is being dragged. This runs every frame so the
        // displayed text tracks the thumb, but it is skipped while the box has focus: overwriting a
        // half-typed value would fight the player's own input, which is exactly what commit/caret
        // handling elsewhere is careful to avoid.
        mirrorSliderIntoField();

        if (this.fieldInSeparateRow) {
            return;
        }

        // Position is written every frame here because the row's x changes with the window size.
        // The field's own visible text is never rewritten unless the value changed, so this does not
        // fight the caret while typing.
        this.multiplierField.setX(x + SLIDER_WIDTH + WIDGET_GAP);
        this.multiplierField.setY(y + 1);
        this.multiplierField.setEditable(isEditable());
        this.multiplierField.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.fieldInSeparateRow && this.multiplierField.mouseClicked(event, doubleClick)) {
            return true;
        }
        // A click anywhere else counts as finishing the edit, so the typed value is applied.
        if (this.fieldWasFocused) {
            commitField();
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.multiplierField.isFocused()) {
            int keyCode = event.key();
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitField();
                this.multiplierField.setFocused(false);
                return true;
            }
            return this.multiplierField.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.multiplierField.isFocused()) {
            return this.multiplierField.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        List<GuiEventListener> children = new ArrayList<>(super.children());
        if (!this.fieldInSeparateRow) {
            children.add(this.multiplierField);
        }
        return children;
    }

    /** Cloth exposes no focus-change callback, so the transition is observed during render. */
    private void commitIfFocusJustLost() {
        boolean focusedNow = this.multiplierField.isFocused();
        if (this.fieldWasFocused && !focusedNow) {
            commitField();
        }
        this.fieldWasFocused = focusedNow;
    }

    /** Unparseable text, including an empty box, reverts to the current value. */
    private void commitField() {
        MultiplierFormat.parse(this.multiplierField.getValue()).ifPresentOrElse(
                parsed -> {
                    applySliderValue(toSlider(parsed));
                    this.multiplierField.setValue(MultiplierFormat.format(parsed));
                },
                () -> this.multiplierField.setValue(MultiplierFormat.format(fromSlider(getValue())))
        );
    }

    /**
     * Copies the slider's current value into the text box so dragging updates it live.
     *
     * <p>Skipped while the box is focused, and when the text already matches, so it cannot interrupt
     * typing or thrash the field's caret state.
     */
    void mirrorSliderIntoField() {
        if (this.multiplierField.isFocused()) {
            return;
        }

        // The slider stores discrete steps, so format from the same source the label uses.
        String target = MultiplierFormat.format(fromSlider(getValue()));
        if (!target.equals(this.multiplierField.getValue())) {
            this.multiplierField.setValue(target);
        }
    }

    /**
     * {@code IntegerSliderEntry#setValue} is deprecated but is the only way to push a value in from
     * outside, and Cloth otherwise only writes to the config when the screen closes.
     */
    @SuppressWarnings("deprecation")
    private void applySliderValue(int sliderValue) {
        setValue(sliderValue);
        this.multiplierField.setValue(MultiplierFormat.format(fromSlider(sliderValue)));
        save();
    }

    /** Shared by the slider's own reset button and the companion row's. */
    public void resetToDefault() {
        applySliderValue(toSlider(1.0D));
    }

    /**
     * Commits the text box from the separate row.
     *
     * <p>{@code ManualEntryRow} renders this entry's field, so it owns the keyboard events for it,
     * but the parsed value and the slider both live here. This exposes the commit step so the row
     * can apply what was typed without duplicating the parsing logic.
     */
    void commitFieldFromCompanion() {
        commitField();
    }

    private static int toSlider(double multiplier) {
        return (int) Math.round(FasterFlightConfig.clamp(multiplier) * SLIDER_SCALE);
    }

    private static double fromSlider(int sliderValue) {
        return FasterFlightConfig.clamp(sliderValue / (double) SLIDER_SCALE);
    }
}
