package uci.bcd2ptz.gui;

import javax.swing.*;
import java.awt.*;

public class VerticalSeparator extends JComponent {

    public VerticalSeparator() {

        setPreferredSize(new Dimension(2, 50));
        setMinimumSize(new Dimension(2, 10));
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        int x = getWidth() / 2;

        g2.setColor(new Color(180, 180, 180));
        g2.setStroke(new BasicStroke(2f));

        g2.drawLine(x, 0, x, getHeight());

        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(2, 1);
            }
}