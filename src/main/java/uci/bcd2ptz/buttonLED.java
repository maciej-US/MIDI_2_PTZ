package uci.bcd2ptz;

import javax.swing.*;
import java.awt.*;

public class buttonLED extends JComponent {

    private boolean isLit = false;
    String label;

    private static final Color LED_ON = Color.decode("#FFBF00");
    private static final Color LED_OFF = new Color(50, 50, 50);

    private static final Color BG = new Color(128, 128, 128);
    private static final Color BORDER = new Color(64, 64, 64);
    private static final Color TEXT = Color.BLACK;

    public buttonLED(String label) {
        Dimension fixedSize = new Dimension(45, 40);
        setPreferredSize(fixedSize);
        setMinimumSize(fixedSize);
        setMaximumSize(fixedSize);
        setSize(fixedSize);
        this.label = label;
    }

    public void setLight(boolean isOn) {
        this.isLit = isOn;
        repaint();
    }

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


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // --- obudowa przycisku ---

        GradientPaint bgGradient = new GradientPaint(
                0, 0, new Color(200, 200, 200),
                0, h, new Color(120, 120, 120)
        );

        g2.setPaint(bgGradient);
        g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);


        // lekki cień
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawRoundRect(3, 3, w - 6, h - 6, 10, 10);

        // ramka
        g2.setColor(BORDER);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(2, 2, w - 4, h - 4, 10, 10);

        // --- napis ---
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();

        int textWidth = fm.stringWidth(label);
        int textX = (w - textWidth) / 2;
        int textY = 16;

        g2.setColor(TEXT);
        g2.drawString(label, textX, textY);


        int ledWidth = 20;
        int ledHeight = 10;
        int ledX = (w - ledWidth) / 2;
        int ledY = h - ledHeight - 5;

        // glow
        if (isLit) {
            g2.setColor(new Color(255, 191, 0, 80));
            g2.fillRoundRect(ledX - 2, ledY - 2, ledWidth + 4, ledHeight + 4, 8, 8);
        }

        // LED
        g2.setColor(isLit ? LED_ON : LED_OFF);
        g2.fillRoundRect(ledX, ledY, ledWidth, ledHeight, 8, 8);

        // border
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(ledX, ledY, ledWidth, ledHeight, 8, 8);

        g2.dispose();
    }
}