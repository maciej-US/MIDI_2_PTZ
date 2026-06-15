package uci.bcd2ptz;

import javax.swing.*;
import java.awt.*;

public class VerticalSlider extends JPanel {

    private int value = 64; // 0–127
    private boolean joystickMode = false;

    public void setValue(int value) {
        this.value = Math.max(0, Math.min(127, value));
        repaint();
    }


    public void setJoystickMode(boolean joystickMode) {
        this.joystickMode = joystickMode;
        repaint();
    }


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

        // --- skala (tło toru) ---

        if (joystickMode) {
            g2.setStroke(new BasicStroke(3f)); // grubszy w joystick
        } else {
            g2.setStroke(new BasicStroke(2f));
        }

        g2.fillRoundRect(centerX - trackWidth / 2, top, trackWidth, bottom - top, 6, 6);

        // --- podświetlenie toru ---
        GradientPaint trackGradient = new GradientPaint(
                centerX, top,
                new Color(80, 80, 80),
                centerX, bottom,
                new Color(20, 20, 20)
        );
        g2.setPaint(trackGradient);
        g2.fillRoundRect(centerX - trackWidth / 2, top, trackWidth, bottom - top, 6, 6);

        // --- przeliczenie pozycji ---
        double normalized = value / 127.0;
        int y = (int) ((bottom - top) * (1 - normalized)) + top;

        g2.setColor(new Color(255, 191, 0, 120));

        // środek (neutral)
        int centerY = (int) ((bottom - top) * (1 - 64.0 / 127.0)) + top;

        if (joystickMode) {

            // ✅ JOYSTICK MODE – od środka
            if (y < centerY) {
                // ruch w górę
                g2.fillRoundRect(centerX - 3, y, 6, centerY - y, 4, 4);
            } else {
                // ruch w dół
                g2.fillRoundRect(centerX - 3, centerY, 6, y - centerY, 4, 4);
            }

        } else {

            // ✅ ABSOLUTE MODE – od dołu
            g2.fillRoundRect(centerX - 3, y, 6, bottom - y, 4, 4);
        }

        // --- SKALA PO LEWEJ ---
        int scaleX = centerX - 25;

        g2.setColor(new Color(120, 120, 120));

        // co 16 jednostek (~równy podział)
        for (int i = 0; i <= 127; i += 16) {
            double norm = i / 127.0;
            int ty = (int) ((bottom - top) * (1 - norm)) + top;

            int tickWidth = (i == 64) ? 10 : 5; // środek większy

            // grubsza linia dla środka
            if (i == 64) {
                g2.setStroke(new BasicStroke(2f));
            } else {
                g2.setStroke(new BasicStroke(1f));
            }

            g2.drawLine(scaleX - tickWidth, ty, scaleX, ty);
        }

        // --- suwak (fader cap) ---
        int knobWidth = 40;
        int knobHeight = 16;

        int knobX = centerX - knobWidth / 2;
        int knobY = y - knobHeight / 2;

        // cień
        g2.setColor(new Color(0, 0, 0, 80));
        g2.fillRoundRect(knobX + 2, knobY + 2, knobWidth, knobHeight, 6, 6);

        // gradient suwaka
        GradientPaint knobGradient = new GradientPaint(
                knobX, knobY,
                new Color(220, 220, 220),
                knobX, knobY + knobHeight,
                new Color(120, 120, 120)
        );

        g2.setPaint(knobGradient);
        g2.fillRoundRect(knobX, knobY, knobWidth, knobHeight, 6, 6);

        // obramowanie
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(knobX, knobY, knobWidth, knobHeight, 6, 6);

        // kreska na środku (jak w realnym faderze)
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(centerX, knobY + 3, centerX, knobY + knobHeight - 3);

        g2.dispose();
    }

}
