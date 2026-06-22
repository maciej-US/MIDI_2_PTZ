package uci.bcd2ptz;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

public class VerticalSlider extends JPanel {

    private int value = 64; // 0–127
    private boolean joystickMode = false;

    private boolean dragging = false;

    // ✅ callback do Core
    private Consumer<Integer> onValueChange;

    public VerticalSlider() {

        // klik / release
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragging = true;
                updateValueFromMouse(e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;

                if (joystickMode) {
                    setValue(64);
                    fireValueChanged();
                }
            }
        });

        // drag
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging) {
                    updateValueFromMouse(e.getY());
                }
            }
        });
    }

    // ==================== API ====================

    public void setOnValueChange(Consumer<Integer> callback) {
        this.onValueChange = callback;
    }

    private void fireValueChanged() {
        if (onValueChange != null) {
            onValueChange.accept(value);
        }
    }

    public void setValue(int value) {
        this.value = Math.max(0, Math.min(127, value));
        repaint();
    }

    public int getValue() {
        return value;
    }

    public void setJoystickMode(boolean joystickMode) {
        this.joystickMode = joystickMode;
        repaint();
    }

    // ==================== LOGIKA MYSZY ====================

    private void updateValueFromMouse(int mouseY) {

        int height = getHeight();

        int top = 15;
        int bottom = height - 15;

        // clamp
        mouseY = Math.max(top, Math.min(bottom, mouseY));

        double norm = (double) (mouseY - top) / (bottom - top);

        int newValue = (int) ((1 - norm) * 127);

        if (newValue != value) {
            value = newValue;
            repaint();
            fireValueChanged();
        }
    }

    // ==================== RYSOWANIE ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        int trackWidth = 8;
        int centerX = width / 2;

        int top = 15;
        int bottom = height - 15;

        // --- tor ---
        g2.setStroke(new BasicStroke(joystickMode ? 3f : 2f));

        GradientPaint trackGradient = new GradientPaint(
                centerX, top,
                new Color(80, 80, 80),
                centerX, bottom,
                new Color(20, 20, 20)
        );
        g2.setPaint(trackGradient);
        g2.fillRoundRect(centerX - trackWidth / 2, top, trackWidth, bottom - top, 6, 6);

        // --- pozycja ---
        double normalized = value / 127.0;
        int y = (int) ((bottom - top) * (1 - normalized)) + top;

        int centerY = (int) ((bottom - top) * (1 - 64.0 / 127.0)) + top;

        g2.setColor(new Color(255, 191, 0, 120));

        if (joystickMode) {

            if (y < centerY) {
                g2.fillRoundRect(centerX - 3, y, 6, centerY - y, 4, 4);
            } else {
                g2.fillRoundRect(centerX - 3, centerY, 6, y - centerY, 4, 4);
            }

        } else {
            g2.fillRoundRect(centerX - 3, y, 6, bottom - y, 4, 4);
        }

        // --- skala ---
        int scaleX = centerX - 25;

        g2.setColor(new Color(120, 120, 120));

        for (int i = 0; i <= 127; i += 16) {
            int ty = (int) ((bottom - top) * (1 - i / 127.0)) + top;
            int tickWidth = (i == 64) ? 10 : 5;

            g2.setStroke(new BasicStroke(i == 64 ? 2f : 1f));
            g2.drawLine(scaleX - tickWidth, ty, scaleX, ty);
        }

        // --- gałka ---
        int knobWidth = 40;
        int knobHeight = 16;

        int knobX = centerX - knobWidth / 2;
        int knobY = y - knobHeight / 2;

        g2.setColor(new Color(0, 0, 0, 80));
        g2.fillRoundRect(knobX + 2, knobY + 2, knobWidth, knobHeight, 6, 6);

        GradientPaint knobGradient = new GradientPaint(
                knobX, knobY,
                new Color(220, 220, 220),
                knobX, knobY + knobHeight,
                new Color(120, 120, 120)
        );

        g2.setPaint(knobGradient);
        g2.fillRoundRect(knobX, knobY, knobWidth, knobHeight, 6, 6);

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(knobX, knobY, knobWidth, knobHeight, 6, 6);

        g2.drawLine(centerX, knobY + 3, centerX, knobY + knobHeight - 3);

        g2.dispose();
    }
}
