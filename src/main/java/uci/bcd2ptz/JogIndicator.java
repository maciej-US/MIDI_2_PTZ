package uci.bcd2ptz;

import javax.swing.*;
import java.awt.*;

public class JogIndicator extends JPanel {

    private Color leftColor = Color.GRAY;
    private Color rightColor = Color.GRAY;
    private Timer resetTimer;

    public void updateDirection(int value) {

        int diff = value - 64;

        // reset
        leftColor = Color.GRAY;
        rightColor = Color.GRAY;

        if (diff != 0) {

            boolean slow = Math.abs(diff) == 1;
            Color activeColor = slow ? Color.GREEN : Color.RED;

            if (diff != 0) {

                int absDiff = Math.abs(diff);

                if (absDiff == 1) {
                    activeColor = Color.GREEN;
                } else if (absDiff <= 3) { // 2 lub 3
                    activeColor = Color.YELLOW;
                } else {
                    activeColor = Color.RED;
                }

                if (diff > 0) {
                    // prawo →
                    rightColor = activeColor;
                } else {
                    // lewo ←
                    leftColor = activeColor;
                }
            }
        }


        if (resetTimer != null) {
            resetTimer.stop();
        }

        resetTimer = new Timer(100, e -> {
            leftColor = Color.GRAY;
            rightColor = Color.GRAY;
            repaint();
        });
        resetTimer.setRepeats(false);
        resetTimer.start();


        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int w = getWidth();
        int h = getHeight();

        int midY = h / 2;

        // LEWA strzałka
        g.setColor(leftColor);
        int[] xLeft = {20, 40, 40};
        int[] yLeft = {midY, midY - 20, midY + 20};
        g.fillPolygon(xLeft, yLeft, 3);

        // PRAWA strzałka
        g.setColor(rightColor);
        int[] xRight = {w - 20, w - 40, w - 40};
        int[] yRight = {midY, midY - 20, midY + 20};
        g.fillPolygon(xRight, yRight, 3);
    }
}
