package uci.midi2ptz.gui;

import javax.swing.*;
import java.awt.*;

public class HorizontalSeparator extends JComponent {

    public HorizontalSeparator() {

        setPreferredSize(new Dimension(100, 4));
        setMinimumSize(new Dimension(50, 4));
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

        int y = getHeight() / 2;

        g2.setColor(new Color(180, 180, 180));
        g2.setStroke(new BasicStroke(2f));

        g2.drawLine(0, y, getWidth(), y);

        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1, 2);

    }
}