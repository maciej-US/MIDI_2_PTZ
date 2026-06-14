package uci.bcd2ptz;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

public class JogIndicator extends JComponent {

    private int direction = 0; // -1 = lewo, 1 = prawo, 0 = stop
    private int speed = 0;     // 0–3
    private double angle = 0.0;

    private final Timer timer;

    private static final Color LED_ON = Color.decode("#FFBF00");
    private static final Color LED_OFF = new Color(60, 60, 60);
    private static final Color WHEEL_COLOR = new Color(120, 120, 120);

    public JogIndicator() {
        setPreferredSize(new Dimension(240, 140));

        timer = new Timer(30, e -> {
            if (speed > 0 && direction != 0) {
                angle += direction * speed * 2.0;
                repaint();
            }
        });
        timer.setRepeats(true);
    }

    /**
     * direction: -1 (lewo), 0 (stop), 1 (prawo)
     * speed: 0–3
     */
    public void setJog(int direction, int speed) {
        this.direction = direction;
        this.speed = speed;

        if (speed == 0 || direction == 0) {
            timer.stop();
        } else {
            if (!timer.isRunning()) {
                timer.start();
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // --- Koło ---
        int wheelDiameter = 100;
        int wheelX = (w - wheelDiameter) / 2;
        int wheelY = h - wheelDiameter - 10;

        AffineTransform old = g2.getTransform();
        g2.translate(wheelX + wheelDiameter / 2.0, wheelY + wheelDiameter / 2.0);
        g2.rotate(Math.toRadians(angle));

        g2.setColor(WHEEL_COLOR);
        g2.fillOval(-wheelDiameter / 2, -wheelDiameter / 2, wheelDiameter, wheelDiameter);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(3f));
        g2.drawOval(-wheelDiameter / 2, -wheelDiameter / 2, wheelDiameter, wheelDiameter);

        // znacznik na kole
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(4f));
        g2.drawLine(0, -wheelDiameter / 2 + 8, 0, -wheelDiameter / 2 + 25);

        g2.setTransform(old);

        // --- LED-y po lewej i prawej w jednej linii ---
        int ledWidth = 20;
        int ledHeight = 14;

        int centerY = wheelY - 20;

        // odstęp między LED-ami
        int spacing = 6;

        // pozycje startowe
        int leftStartX = w / 2 - 90;   // lewa grupa
        int rightStartX = w / 2 + 30;  // prawa grupa

        // LEWA grupa <<< (wierzchołki w LEWO)
        for (int i = 0; i < 3; i++) {
            int cx = leftStartX + i * (ledWidth + spacing);
            int cy = centerY;

            Polygon tri = new Polygon();
            tri.addPoint(cx, cy - ledHeight / 2);      // góra
            tri.addPoint(cx, cy + ledHeight / 2);      // dół
            tri.addPoint(cx - ledWidth, cy);           // wierzchołek w LEWO

            if (direction == -1 && speed >= i + 1) {
                g2.setColor(LED_ON);
            } else {
                g2.setColor(LED_OFF);
            }
            g2.fillPolygon(tri);

            g2.setColor(Color.BLACK);
            g2.drawPolygon(tri);
        }

        // PRAWA grupa >>> (wierzchołki w PRAWO)
        for (int i = 0; i < 3; i++) {
            int cx = rightStartX + i * (ledWidth + spacing);
            int cy = centerY;

            Polygon tri = new Polygon();
            tri.addPoint(cx, cy - ledHeight / 2);      // góra
            tri.addPoint(cx, cy + ledHeight / 2);      // dół
            tri.addPoint(cx + ledWidth, cy);           // wierzchołek w PRAWO

            if (direction == 1 && speed >= i + 1) {
                g2.setColor(LED_ON);
            } else {
                g2.setColor(LED_OFF);
            }
            g2.fillPolygon(tri);

            g2.setColor(Color.BLACK);
            g2.drawPolygon(tri);
        }

        g2.dispose();
    }
}
