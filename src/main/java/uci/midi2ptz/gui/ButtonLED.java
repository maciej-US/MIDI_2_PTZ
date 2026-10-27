package uci.midi2ptz.gui;

import lombok.Setter;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
/**
 * Custom Swing button component with integrated LED indicator.
 *
 * <p>The component simulates a hardware control-panel button commonly found on MIDI controllers, PTZ panels and broadcast
 * control surfaces.</p>
 *
 * <p>In addition to standard button behavior, the component provides an integrated status LED supporting three operating modes:</p>
 *
 * <ul>
 *     <li>{@link LedMode#OFF}</li>
 *     <li>{@link LedMode#ON}</li>
 *     <li>{@link LedMode#BLINK}</li>
 * </ul>
 *
 * <p>All blinking indicators share a common timer-based animation engine to minimize resource usage.</p>
 * <p>The component is primarily used for camera-selection, tally, connection-status and cue indicators within the MIDI2PTZ control interface.</p>
 */
public class ButtonLED extends JComponent {

    /**
     * Supported LED operating modes.
     */
    public enum LedMode {
        OFF,                /** LED is disabled.*/
        ON,                 /** LED remains continuously illuminated.*/
        BLINK               /** LED periodically toggles between on and off states.*/
    }

    /**
     * Current LED operating mode.
     */
    private LedMode mode = LedMode.OFF;

    /**
     * Text displayed on the button.
     */
    private final String label;

    /**
     * Color used when the LED is active.
     */
    private final Color LED_ON;

    /**
     * Color used when the LED is inactive.
     */
    private static final Color LED_OFF = new Color(50, 50, 50);

    private static final Color BORDER = new Color(0, 0, 0);
    private static final Color TEXT = Color.BLACK;

    // ===== GLOBALNY BLINK ENGINE =====
    private static boolean blinkState = false;
    private static final List<ButtonLED> instances = new ArrayList<>();

    /**
     * Shared LED blinking engine.
     *
     * <p>A single Swing timer periodically toggles the global blink state and repaints only those components currently
     * operating in {@link LedMode#BLINK} mode.</p>
     */
    static {
        // ~3 Hz (state switch every 166 ms)
        Timer blinkTimer = new Timer(166, e -> {
            blinkState = !blinkState;
            for (ButtonLED b : instances) {
                if (b.mode == LedMode.BLINK) {
                    b.repaint();
                }
            }
        });
        blinkTimer.start();
    }

    /**
     * Registered click handler.
     */
    @Setter
    private Runnable onClick;

    /**
     * Indicates whether the button is currently pressed.
     */
    private boolean pressed = false;

    /**
     * Creates a button with an integrated LED indicator.
     *
     * @param label button caption
     * @param ledOnColor LED color used in active state
     */
    public ButtonLED(String label, Color ledOnColor) {
        this.label = label;
        this.LED_ON = ledOnColor;

        Dimension fixedSize = new Dimension(45, 40);
        setPreferredSize(fixedSize);
        setMinimumSize(fixedSize);
        setMaximumSize(fixedSize);

        addMouseListener(new java.awt.event.MouseAdapter() {

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {

                pressed = false;
                repaint();

                // klik jeśli kursor nadal w komponencie
                if (contains(e.getPoint())) {
                    fireClick();
                }
            }
        });

        instances.add(this);
    }

    /**
     * Sets the LED operating mode.
     *
     * <p>The visual representation is updated immediately.</p>
     *
     * @param mode target LED mode
     */
    public void setMode(LedMode mode) {
        this.mode = mode;
        repaint();
    }

    /**
     * Executes the registered click action.
     */
    private void fireClick() {
        if (!isEnabled()){
            return;
        }
        if (onClick != null) {
            onClick.run();
        }
    }


    // ===== FIXED DIMENSIONS =====
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(45, 40);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(45, 40);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(45, 40);
    }

    /**
     * Renders the button body, text caption and LED indicator.
     *
     * <p>The component uses a custom control-surface style appearance with gradient shading, rounded borders
     * and a hardware-inspired LED display.</p>
     *
     * <p>When the button is pressed, the graphic is slightly translated to simulate physical switch movement.</p>
     *
     * @param g graphics context
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // === TŁO (jak slider) ===
        GradientPaint bgGradient = new GradientPaint(
                0, 0, new Color(200, 200, 200),
                0, h, new Color(120, 120, 120)
        );


        if (pressed) {
            g2.translate(1, 1);
        }

        g2.setPaint(bgGradient);
        g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);

        // cień
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawRoundRect(3, 3, w - 6, h - 6, 10, 10);

        // ramka
        g2.setColor(BORDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(2, 2, w - 4, h - 4, 8, 8);

        // === TEKST ===
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();

        int textWidth = fm.stringWidth(label);
        int textX = (w - textWidth) / 2;
        int textY = 14;

        g2.setColor(TEXT);
        g2.drawString(label, textX, textY);

        // === LED ===
        int ledWidth = 20;
        int ledHeight = 10;
        int ledX = (w - ledWidth) / 2;
        int ledY = h - ledHeight - 5;

        // obliczenie stanu LED
        boolean ledOn = switch (mode) {
            case ON -> true;
            case BLINK -> blinkState;
            default -> false;
        };

        // glow
        if (ledOn) {
            g2.setColor(new Color(255, 191, 0, 70));
            g2.fillRoundRect(ledX - 2, ledY - 2, ledWidth + 4, ledHeight + 4, 8, 8);
        }

        // LED właściwy
        g2.setColor(ledOn ? LED_ON : LED_OFF);
        g2.fillRoundRect(ledX, ledY, ledWidth, ledHeight, 8, 8);

        // border LED
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(ledX, ledY, ledWidth, ledHeight, 8, 8);

        g2.dispose();
    }
}