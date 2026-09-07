package uci.bcd2ptz.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;

public class JogIndicator extends JComponent {

    // ================= COMMUNICATION ===================
    private int direction = 0; // -1 = lewo, 1 = prawo, 0 = stop
    private int speed = 0;     // 0–3
    private double angle = 0.0;
    private boolean suppressEvent = false;
    private boolean joystickMode = true;


    private boolean dragging = false;
    private int startX = 0;
    private static final int DEAD_ZONE = 15;
    private int currentMouseSpeed = 0;
    private int currentMouseDirection = 0;

    private java.util.function.BiConsumer<Integer, Integer> onJogChange;

    // ============ UX params ==================
    private Timer timer = null;
    private long lastUpdateTime = 0;
    private int displaySpeed = 0;
    private long lastSpeedUpdateTime = 0;
    private static final int HOLD_MS = 140;

    // ====================== VISUALS =====================
    private static final Color LED_ON = Color.decode("#FFBF00");
    private static final Color LED_OFF = new Color(60, 60, 60);
    private static final Color WHEEL_COLOR = new Color(120, 120, 120);

    public JogIndicator() {
        setPreferredSize(new Dimension(240, 140));

        timer = new Timer(30, e -> {

            long now = System.currentTimeMillis();

            // ✅ WATCHDOG – brak update = STOP
            if (now - lastUpdateTime > 150) {
                speed = 0;
                direction = 0;
            }

            if (speed > 0 && direction != 0) {
                angle += direction * speed * 2.0;
                repaint();
            } else {
                timer.stop();
                repaint();
            }
        });

        timer.setRepeats(true);

        addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                dragging = true;
                startX = e.getX();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;

                currentMouseDirection = 0;
                currentMouseSpeed = 0;

                if (!suppressEvent && onJogChange != null) {
                    onJogChange.accept(0, 0);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {

                int centerX = getWidth() / 2;

                if (e.getX() > centerX) {
                    setJog(1, 1);

                    if (onJogChange != null) {
                        onJogChange.accept(1, 1);
                    }

                } else {
                    setJog(-1, 1);

                    if (onJogChange != null) {
                        onJogChange.accept(-1, 1);
                    }
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {

                if (!dragging) {return;}

                int delta = e.getX() - startX;

                if (Math.abs(delta) < DEAD_ZONE) {
                    currentMouseDirection = 0;
                    currentMouseSpeed = 0;
                    return;
                }

                currentMouseDirection = delta > 0 ? 1 : -1;

                currentMouseSpeed =Math.min(5, Math.abs(delta) / 20 + 1);

                if (!suppressEvent && onJogChange != null) {

                    onJogChange.accept(
                            currentMouseDirection,
                            currentMouseSpeed
                    );
                }
            }

        });

    }

    // ============== API ====================

    public void setOnJogChange(java.util.function.BiConsumer<Integer, Integer> listener) {
        this.onJogChange = listener;
    }

    public void setJoystickMode (boolean joystickMode) {
        this.joystickMode = joystickMode;
    }

    public void setValueSilently(int value) {

        //DEBUG:
        //System.out.println ("======= JOG CLASS ========== \n Received VAL = " + value);
        suppressEvent = true;

        int direction = Integer.compare(value, 0);
        int speed =Math.abs(value);

        //DEBUG
        //System.out.println("JOG SILENT dir=" + direction + " speed=" + speed);
        setJog(direction, speed);

        suppressEvent = false;
    }

    /**
     * direction: -1 (lewo), 0 (stop), 1 (prawo)
     * speed: 0–5
     */
    public void setJog(int direction, int speed) {

        this.direction = direction;
        this.speed = speed;

        // ✅ latch – zapamiętaj max prędkość
        if (speed > displaySpeed) {
            displaySpeed = speed;
        }

        lastSpeedUpdateTime = System.currentTimeMillis();

        // ✅ update watchdog
        this.lastUpdateTime = System.currentTimeMillis();

        if (speed == 0 || direction == 0) {
            timer.stop();
        } else {
            if (!timer.isRunning()) {
                timer.start();
            }
        }

        repaint();
    }

    // ==================== ViSUALS =======================

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
        int leftStartX = w / 2 - 70;   // lewa grupa
        int rightStartX = w / 2 + 10;  // prawa grupa

        //kontrola zapalania
        int ledLevel = getLedLevel(this.speed);

        // LEWA
        for (int i = 0; i < 3; i++) {
            int cx = leftStartX + i * (ledWidth + spacing);
            int cy = centerY;

            Polygon tri = new Polygon();
            tri.addPoint(cx, cy - ledHeight / 2);
            tri.addPoint(cx, cy + ledHeight / 2);
            tri.addPoint(cx - ledWidth, cy);

            if (direction == -1 && ledLevel >= 3 -i ) {
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

            if (direction == 1 && ledLevel >= i + 1) {
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

    /*=========================================
            ADD-ONS, HELPERS,
      ========================================= */

    private int getLedLevel(int speed) {

        if (speed <= 0) {return 0;}
        if (speed == 1) {return 1;}
        if (speed <= 3) {return 2;}
        return 3;
    }
}
