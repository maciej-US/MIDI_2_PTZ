package uci.bcd2ptz;

import javax.swing.*;
import java.awt.*;

public class VerticalSlider extends JPanel {

    private int value = 0; // 0–127

    public void setValue(int value) {
        this.value = Math.max(0, Math.min(127, value));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int width = getWidth();
        int height = getHeight();

        // środkowa oś
        int centerX = width / 2;
        g.drawLine(centerX, 10, centerX, height - 10);

        // przeliczenie wartości (0-127 → dół-góra)
        double normalized = value / 127.0;
        int y = (int) ((height - 20) * (1 - normalized)) + 10;

        // "suwak"
        g.fillRect(centerX - 10, y - 5, 20, 10);
    }
}
