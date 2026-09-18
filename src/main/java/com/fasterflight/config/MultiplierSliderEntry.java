package com.fasterflight.config;

import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * A Cloth Config entry that pairs an integer slider with a manual-entry text box.
 *
 * <p>Cloth Config has no built-in entry that keeps a slider and a free-text field in step, so this
 * wraps an {@link IntegerSliderEntry} and adds a {@link TextFieldWidget}. The two stay synchronised
 * in both directions: dragging the slider reformats the text box, and committing the text box
 * (Enter, or clicking away) moves the slider and persists the new value.
 *
 * <p>Cloth's slider widget is integer-based, so values are stored as whole slider steps. The
 * displayed multiplier is that integer divided by {@link #SLIDER_SCALE}, which gives the required
 * 0.1 resolution across the supported 1.0x to 25.0x range.
 *
 * <p>The text box can optionally be handed to a different screen row via
 * {@link #borrowFieldForSeparateRow()}; when that happens this entry stops rendering and
 * hit-testing the widget so the two rows do not fight over it.
 */
public class MultiplierSliderEntry extends IntegerSliderEntry {

    /** Slider steps per 1.0x of multiplier. Ten steps yields the required 0.1 granularity. */
    public static final int SLIDER_SCALE = 10;

    /** Slider width, matching Cloth Config's own default so the row keeps a familiar shape. */
    private static final int SLIDER_WIDTH = 118;

    /** Text box width; enough for "25.0" with a little padding. */
    private static final int FIELD_WIDTH = 42;

    /** Height shared by the slider and the text box. */
    private static final int WIDGET_HEIGHT = 20;

    /** Horizontal gap between the slider and the text box. */
    private static final int WIDGET_GAP = 4;

    private final TextFieldWidget multiplierField;

    /**
     * Left edge of the slider as of the last render, for the row beneath to align to.
     *
     * <p>The slider is an inaccessible inner class, so its position cannot be read directly; it is
     * captured while this row draws instead, which is reliable because the row that consumes the
     * value sits directly below this one and renders straight after it.
     */
    private int sliderLeft = 0;

    /** Width of the slider as of the last render, for the row beneath to align to. */
    private int sliderWidth = SLIDER_WIDTH;

    /** Tracks focus so a commit fires on the frame the field loses focus. */
    private boolean fieldWasFocused = false;

    /**
     * When true the text box is rendered by a separate screen row instead of beside the slider, so
     * this entry must not draw or hit-test it.
     */
    private boolean fieldInSeparateRow = false;

    /**
     * @param fieldName      the label shown to the left of the slider row
     * @param value          the initial multiplier, in real units rather than slider steps
     * @param onValueChanged called with the new multiplier whenever the value is committed
     */
    public MultiplierSliderEntry(Text fieldName, double value, DoubleConsumer onValueChanged) {
        super(
                fieldName,
                toSlider(FasterFlightConfig.MIN_MULTIPLIER),
                toSlider(FasterFlightConfig.MAX_MULTIPLIER),
                toSlider(value),
                Text.translatable("text.cloth-config.reset_value"),
                () -> toSlider(1.0D),
                sliderValue -> onValueChanged.accept(fromSlider(sliderValue))
        );

        setTextGetter(sliderValue -> MultiplierFormat.toText(fromSlider(sliderValue)));

        this.multiplierField = new TextFieldWidget(
                MultiplierFormat.font(),
                0, 0, FIELD_WIDTH, WIDGET_HEIGHT,
                Text.translatable("fasterflight.config.speedMultiplier")
        ) {
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                boolean handled = super.mouseClicked(mouseX, mouseY, button);
                if (handled && button == 1) {
                    // Right click clears the box so a new value can be typed straight away, matching
                    // how vanilla text fields behave.
                    setText("");
                }
                return handled;
            }
        };
        this.multiplierField.setMaxLength(6);
        this.multiplierField.setText(MultiplierFormat.format(value));
        // Restricts typing to characters a decimal multiplier can legitimately contain. Two digits
        // before the separator keeps the 25x maximum reachable.
        this.multiplierField.setTextPredicate(text -> text.isEmpty() || text.matches("\\d{0,2}([.,]\\d?)?x?"));
        // Deliberately no changed listener. Reacting to each keystroke would rewrite the text box
        // mid-edit, which makes deleting digits impossible because the rewritten text puts the
        // character straight back. Typed values are applied on commit instead.
    }

    /** @return the text box widget. */
    public TextFieldWidget getMultiplierField() {
        return this.multiplierField;
    }

    /**
     * Records the slider's on-screen box so the row beneath can match it exactly.
     *
     * <p>The slider's declared type is a private inner class, so {@code getX()} and {@code getWidth()}
     * are not visible at compile time. Its runtime type extends {@code ClickableWidget}, whose {@code x}
     * field and {@code getWidth()} method are public, so the box is read through that supertype. Cloth
     * sizes the label column from the longest field name rather than a constant, which is why the
     * values are read from the laid-out widget instead of being recomputed.
     *
     * @param rowX        the row's left edge, used as a fallback
     * @param rowWidth    the row's width, used as a fallback
     */
    private void captureSliderGeometry(int rowX, int rowWidth) {
        Object widget = this.sliderWidget;
        if (widget instanceof ClickableWidget clickable) {
            this.sliderLeft = clickable.x;
            this.sliderWidth = clickable.getWidth();
            return;
        }

        // Should not happen, but keeps the row beneath sane rather than collapsing it.
        this.sliderLeft = rowX;
        this.sliderWidth = Math.max(SLIDER_WIDTH, rowWidth);
    }

    /**
     * @return the slider's left edge as recorded during this row's last render.
     */
    public int getSliderLeft() {
        return this.sliderLeft;
    }

    /** @return the slider's width as recorded during this row's last render. */
    public int getSliderWidth() {
        return this.sliderWidth;
    }

    /**
     * @return the width Cloth gave this row's own reset button, so a companion row can match it.
     *         Falls back to the standard button width if the widget is not available.
     */
    public int getResetButtonWidth() {
        ButtonWidget button = this.resetButton;
        return button != null ? button.getWidth() : 60;
    }

    /**
     * Hands the text box to a separate screen row so it can appear on its own line.
     *
     * <p>The widget instance is reused rather than duplicated, so the slider and the standalone row
     * always show the same value.
     *
     * @return the text box widget, for the borrowing entry to host
     */
    public TextFieldWidget borrowFieldForSeparateRow() {
        this.fieldInSeparateRow = true;
        return this.multiplierField;
    }

    @Override
    public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight,
                       int mouseX, int mouseY, boolean isHovered, float delta) {
        super.render(matrices, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);

        captureSliderGeometry(x, entryWidth);

        commitIfFocusJustLost();

        if (this.fieldInSeparateRow) {
            // Another row owns the text box; drawing it here as well would double-render it.
            return;
        }

        // Place the text box immediately to the right of the slider, which Cloth positions itself.
        this.multiplierField.x = x + SLIDER_WIDTH + WIDGET_GAP;
        this.multiplierField.y = y + 1;
        this.multiplierField.setEditable(isEditable());
        this.multiplierField.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.fieldInSeparateRow) {
            boolean fieldHandled = this.multiplierField.mouseClicked(mouseX, mouseY, button);
            if (fieldHandled) {
                return true;
            }
        }
        // A click anywhere else counts as "done editing" and commits whatever is currently typed.
        if (this.fieldWasFocused) {
            commitField();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.multiplierField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitField();
                this.multiplierField.setTextFieldFocused(false);
                return true;
            }
            return this.multiplierField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.multiplierField.isFocused()) {
            return this.multiplierField.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public List<? extends Element> children() {
        List<Element> children = new ArrayList<>(super.children());
        if (!this.fieldInSeparateRow) {
            children.add(this.multiplierField);
        }
        return children;
    }

    /**
     * Commits the text box on the frame it stops being focused.
     *
     * <p>Cloth Config exposes no focus-change callback, so the transition is observed during render.
     */
    private void commitIfFocusJustLost() {
        boolean focusedNow = this.multiplierField.isFocused();
        if (this.fieldWasFocused && !focusedNow) {
            commitField();
        }
        this.fieldWasFocused = focusedNow;
    }

    /**
     * Reads the text box and applies it.
     *
     * <p>A value that parses is clamped, snapped to the step size and written through to the config.
     * Unparseable text, including an empty box, is reverted to the current value so the field can
     * never be left in an invalid state.
     */
    private void commitField() {
        MultiplierFormat.parse(this.multiplierField.getText()).ifPresentOrElse(
                parsed -> {
                    applySliderValue(toSlider(parsed));
                    this.multiplierField.setText(MultiplierFormat.format(parsed));
                },
                () -> this.multiplierField.setText(MultiplierFormat.format(fromSlider(getValue())))
        );
    }

    /**
     * Writes a value into the slider and persists it.
     *
     * <p>{@code IntegerSliderEntry#setValue} is deprecated but remains the only supported way to push
     * a value in from outside, so the deprecation is suppressed here.
     */
    @SuppressWarnings("deprecation")
    private void applySliderValue(int sliderValue) {
        setValue(sliderValue);
        this.multiplierField.setText(MultiplierFormat.format(fromSlider(sliderValue)));
        // Cloth only pushes values into the config when the screen is saved, so invoking the entry's
        // save callback keeps the JSON file in step with the GUI straight away.
        save();
    }

    /**
     * Restores the multiplier to its 1.0x default and persists it.
     *
     * <p>Exposed so the manual-entry row's reset button can reuse the same code path as the slider's
     * own reset button.
     */
    public void resetToDefault() {
        applySliderValue(toSlider(1.0D));
        this.multiplierField.setText(MultiplierFormat.format(1.0D));
    }

    /** Converts a real multiplier to slider steps. */
    private static int toSlider(double multiplier) {
        return (int) Math.round(FasterFlightConfig.clamp(multiplier) * SLIDER_SCALE);
    }

    /** Converts slider steps back to a real multiplier. */
    private static double fromSlider(int sliderValue) {
        return FasterFlightConfig.clamp(sliderValue / (double) SLIDER_SCALE);
    }
}
