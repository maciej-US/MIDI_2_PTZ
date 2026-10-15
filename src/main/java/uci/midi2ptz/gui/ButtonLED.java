package uci.midi2ptz.gui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ButtonLED extends JComponent {

    // ===== TRYB LED =====
    public enum LedMode {
        OFF,
        ON,
        BLINK
    }

    private LedMode mode = LedMode.OFF;
    private final String label;

    // ===== KOLORY =====
    private final Color LED_ON;
    private static final Color LED_OFF = new Color(50, 50, 50);

    private static final Color BORDER = new Color(0, 0, 0);
    private static final Color TEXT = Color.BLACK;

    // ===== GLOBALNY BLINK ENGINE =====
    private static boolean blinkState = false;
    private static final List<ButtonLED> instances = new ArrayList<>();

    static {
        // OKOŁO 3 Hz (zmiana stanu co 166 ms)
        Timer blinkTimer = new Timer(166, e -> {
            blinkState = !blinkState;

            // repaint tylko tych co migają
            for (ButtonLED b : instances) {
                if (b.mode == LedMode.BLINK) {
                    b.repaint();
                }
            }
        });
        blinkTimer.start();
    }

    //======== Obsługa eventów

    private Runnable onClick;
    private boolean pressed = false;

    // ===== KONSTRUKTOR =====
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

    // ===== API =====
    public void setMode(LedMode mode) {
        //debug
        //System.out.println("LED_BUTTON " + label + " mode triggered. VAL =  " + mode);
        this.mode = mode;
        repaint();
    }


    public void setOnClick(Runnable onClick) {
        this.onClick = onClick;
    }

    private void fireClick() {
        if (!isEnabled()){
            return;
        }
        if (onClick != null) {
            onClick.run();
        }
    }


    // ===== ROZMIAR NA SZTYWNO =====
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

    // ===== RENDER =====
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
        boolean ledOn;

        switch (mode) {
            case ON:
                ledOn = true;
                break;
            case BLINK:
                ledOn = blinkState;
                break;
            default:
                ledOn = false;
        }

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