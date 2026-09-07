package uci.bcd2ptz.gui;

import javax.swing.*;
import java.awt.*;

public class OnAirIndicator extends JComponent {

    private boolean onAir = false;

    public void setOnAir(boolean onAir) {
        this.onAir = onAir;
        repaint();
    }

    public boolean isOnAir() {
        return onAir;
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        int w = getWidth();
        int h = getHeight();

        Color bgColor =onAir ? new Color(210, 20, 20) : new Color(60, 60, 60);
        g2.setColor(bgColor);
        g2.fillRoundRect(2, 2, w - 4, h - 4, 12,12);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2f));

        g2.drawRoundRect(2, 2, w - 4, h - 4, 12, 12);
        g2.setColor(Color.WHITE);

        g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
        FontMetrics fm = g2.getFontMetrics();
        String text = "ON AIR";

        int tx = (w - fm.stringWidth(text)) / 2;
        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(text,tx,ty);

        g2.dispose();
    }
}