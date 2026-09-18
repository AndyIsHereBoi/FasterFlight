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

    private final TextFieldWidget multiplierField;

    private int sliderLeft = 0;
    private int sliderWidth = SLIDER_WIDTH;

    private boolean fieldWasFocused = false;

    /** Set while the text box is drawn by another row, which then owns it. */
    private boolean fieldInSeparateRow = false;

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
                    // Right click clears the box, matching how vanilla text fields behave.
                    setText("");
                }
                return handled;
            }
        };
        this.multiplierField.setMaxLength(6);
        this.multiplierField.setText(MultiplierFormat.format(value));
        // Two digits before the separator keeps the 25x maximum reachable.
        this.multiplierField.setTextPredicate(text -> text.isEmpty() || text.matches("\\d{0,2}([.,]\\d?)?x?"));
        // No changed listener on purpose: reacting to each keystroke would rewrite the box mid-edit,
        // making it impossible to delete a digit because the rewritten text puts it straight back.
    }

    public TextFieldWidget getMultiplierField() {
        return this.multiplierField;
    }

    /**
     * Records the slider's on-screen box for the row beneath to match.
     *
     * <p>The slider's declared type is a private inner class, so its position is read through
     * {@link ClickableWidget}, whose {@code x} field and {@code getWidth()} are public. Reading the
     * laid-out widget is necessary because Cloth sizes the label column from the longest field name
     * rather than a constant, so recomputing it would drift as the window resizes.
     */
    private void captureSliderGeometry(int rowX, int rowWidth) {
        Object widget = this.sliderWidget;
        if (widget instanceof ClickableWidget clickable) {
            this.sliderLeft = clickable.x;
            this.sliderWidth = clickable.getWidth();
            return;
        }

        this.sliderLeft = rowX;
        this.sliderWidth = Math.max(SLIDER_WIDTH, rowWidth);
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
        ButtonWidget button = this.resetButton;
        return button != null ? button.getWidth() : 60;
    }

    /**
     * Hands the text box to another row so it can appear on its own line. The same instance is
     * returned rather than a copy, so both rows share one value, caret and focus state.
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
            return;
        }

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
        // A click anywhere else counts as finishing the edit, so the typed value is applied.
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
        MultiplierFormat.parse(this.multiplierField.getText()).ifPresentOrElse(
                parsed -> {
                    applySliderValue(toSlider(parsed));
                    this.multiplierField.setText(MultiplierFormat.format(parsed));
                },
                () -> this.multiplierField.setText(MultiplierFormat.format(fromSlider(getValue())))
        );
    }

    /**
     * {@code IntegerSliderEntry#setValue} is deprecated but is the only way to push a value in from
     * outside, and Cloth otherwise only writes to the config when the screen closes.
     */
    @SuppressWarnings("deprecation")
    private void applySliderValue(int sliderValue) {
        setValue(sliderValue);
        this.multiplierField.setText(MultiplierFormat.format(fromSlider(sliderValue)));
        save();
    }

    /** Shared by the slider's own reset button and the companion row's. */
    public void resetToDefault() {
        applySliderValue(toSlider(1.0D));
        this.multiplierField.setText(MultiplierFormat.format(1.0D));
    }

    private static int toSlider(double multiplier) {
        return (int) Math.round(FasterFlightConfig.clamp(multiplier) * SLIDER_SCALE);
    }

    private static double fromSlider(int sliderValue) {
        return FasterFlightConfig.clamp(sliderValue / (double) SLIDER_SCALE);
    }
}
