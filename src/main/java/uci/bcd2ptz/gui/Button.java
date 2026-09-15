package uci.bcd2ptz.gui;

import javax.swing.*;
import java.awt.*;

public class Button extends JComponent {

    private final String label;

    private Runnable onClick;
    private boolean pressed = false;

    private static final Color BORDER = new Color(0, 0, 0);
    private static final Color TEXT = Color.BLACK;

    public Button(String label) {

        this.label = label;

        Dimension fixedSize = new Dimension(40, 20);

        setPreferredSize(fixedSize);
        setMinimumSize(fixedSize);
        setMaximumSize(fixedSize);

        addMouseListener(new java.awt.event.MouseAdapter() {

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {

                if (!isEnabled()) {
                    return;
                }

                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {

                if (!isEnabled()) {
                    return;
                }

                pressed = false;
                repaint();

                if (contains(e.getPoint())) {
                    fireClick();
                }
            }
        });
    }

    public void setOnClick(Runnable onClick) {
        this.onClick = onClick;
    }

    private void fireClick() {

        if (onClick != null) {
            onClick.run();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(45, 20);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(45, 20);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(45, 20);
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

        GradientPaint bgGradient = new GradientPaint(
                0, 0, new Color(200, 200, 200),
                0, h, new Color(120, 120, 120)
        );

        if (pressed) {
            g2.translate(1, 1);
        }

        // korpus przycisku
        g2.setPaint(bgGradient);
        g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);

        // cień
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawRoundRect(3, 3, w - 6, h - 6, 10, 10);

        // ramka
        g2.setColor(BORDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(2, 2, w - 4, h - 4, 8, 8);

        // napis
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));

        FontMetrics fm = g2.getFontMetrics();

        int textWidth = fm.stringWidth(label);
        int textX = (w - textWidth) / 2;
        int textY = (h + fm.getAscent() - fm.getDescent()) / 2;

        g2.setColor(TEXT);
        g2.drawString(label, textX, textY);

        g2.dispose();
    }
}