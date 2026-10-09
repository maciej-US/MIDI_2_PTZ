package uci.bcd2ptz.gui;

import uci.bcd2ptz.visca.FieldDefinition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.util.Map;
import java.util.function.Consumer;

public class RotarySwitch extends JComponent {

    private int currentValue;

    private int stateCount;
    private Map<String, String> labels;

    private boolean active = true;
    private boolean suppressEvents;

    private boolean dragging;
    private int dragStartX;
    private int dragStartValue;

    private static final int DEAD_ZONE = 10;
    private static final int PIXELS_PER_STEP = 20;

    private Consumer<Integer> onValueChange;

    //======VISUALS
    private static final int[][] LABEL_OFFSETS_6L = {
            {-11,  3 },  // 0
            { -2,  3 },  // 1
            { -9,  9 },  // 2
            { 18,  9 },  // 3
            {  4,  5 },  // 4
            {  7,  3 }   // 5
    };

    public RotarySwitch() {

        setPreferredSize(new Dimension(80, 60));
        setMinimumSize(new Dimension(80, 60));
        setMaximumSize(new Dimension(80, 60));
        setOpaque(false);

        addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {

                if (!active) {
                    return;
                }

                dragging = true;
                dragStartX = e.getX();
                dragStartValue = currentValue;
            }

            @Override
            public void mouseReleased(MouseEvent e) {

                dragging = false;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {

            @Override
            public void mouseDragged(MouseEvent e) {

                if (!active || !dragging) {
                    return;
                }

                int deltaX = e.getX() - dragStartX;

                if (Math.abs(deltaX) < DEAD_ZONE) {
                    return;
                }

                int effectiveDelta = deltaX > 0 ? deltaX - DEAD_ZONE : deltaX + DEAD_ZONE;
                int stepDelta = effectiveDelta / PIXELS_PER_STEP;
                int newValue = dragStartValue + stepDelta;

                newValue = Math.max(0, Math.min(stateCount - 1, newValue));

                if (newValue != currentValue) {
                    currentValue = newValue;
                    repaint();
                    fireValueChange();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {

        if (stateCount <= 1){
            return;
        }

        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        int w = getWidth();
        int h = getHeight();

        int centerX = w / 2;
        int centerY = h / 2;

        int radius = Math.min(w, h) / 2 - 18;

        // =============================
        // LABELS + POSITIONS
        // =============================

        for (int i = 0; i < stateCount; i++) {

            double angleDeg = - 150.0 + (i * (300.0 / (stateCount - 1)));

            double angle = Math.toRadians(angleDeg);
            boolean selected = i == currentValue;

            int tickOuter = radius + 8;
            int tickInner = radius - 4;

            int x1 = centerX + (int) (Math.sin(angle) * tickInner);
            int y1 = centerY - (int) (Math.cos(angle) * tickInner);

            int x2 = centerX + (int) (Math.sin(angle) * tickOuter);
            int y2 = centerY - (int) (Math.cos(angle) * tickOuter);

            g2.setStroke(new BasicStroke(selected ? 3f : 1.5f));
            g2.setColor(selected ? new Color(255, 200, 40) : new Color(120, 120, 120));

            g2.drawLine(x1, y1, x2, y2);

            // LED
            int ledRadius = radius + 8;
            int ledX = centerX + (int) (Math.sin(angle) * ledRadius);
            int ledY = centerY - (int) (Math.cos(angle) * ledRadius);


            if (selected) {
                g2.setColor(new Color(255, 200, 40));
                g2.fillOval(ledX - 4,ledY - 4,8,8);
            }

            // LABEL
            String label = labels.get(String.valueOf(i));

            if (label != null) {
                int labelRadius =radius + 20;
                int lx = centerX + (int) (Math.sin(angle) * labelRadius);
                int ly = centerY - (int) (Math.cos(angle) * labelRadius);

                //---offsets
                if (i < LABEL_OFFSETS_6L.length) {

                    lx += LABEL_OFFSETS_6L[i][0];
                    ly += LABEL_OFFSETS_6L[i][1];
                }

                //debug
                //System.out.println("Label " + i + " - [" + label + "]" + "x = " + lx + "  y = " + ly);

                if (selected) {
                    g2.setColor(new Color(255, 220, 80));
                    g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
                } else {
                    g2.setColor(new Color(180, 180, 180));
                    g2.setFont(g2.getFont().deriveFont(9f));
                }

                FontMetrics fm = g2.getFontMetrics();
                int width = fm.stringWidth(label);
                g2.drawString(label, lx - width / 2, ly);
            }
        }

        // =============================
        // SWITCH BODY
        // =============================

        Shape body =new Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2, radius * 2 );
        g2.setColor(new Color(60, 60, 60));
        g2.fill(body);
        g2.setColor(new Color(20, 20, 20));
        g2.setStroke(new BasicStroke(2f));
        g2.draw(body);

        // =============================
        //     SELECTOR POINTER
        // =============================

        double angleDeg = - 150.0 + (currentValue * (300.0 / (stateCount - 1)));
        double angle = Math.toRadians(angleDeg);
        int pointerLength = radius - 10;
        int x = centerX + (int) (Math.sin(angle) * pointerLength);
        int y = centerY - (int) (Math.cos(angle) * pointerLength);

        Color pointerColor = active ? new Color(255, 200, 40) : new Color(120, 120, 120);

        g2.setStroke(new BasicStroke( 5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(pointerColor);
        g2.drawLine(centerX,centerY, x, y);

        g2.fillOval(centerX - 6,centerY - 6,12,12);

        g2.dispose();
    }


    /* =========================
               INTERFACE
       ========================*/

    private void fireValueChange(){
        if (suppressEvents){
            return;
        }
        if (onValueChange != null){
            int midiValue;
            if (stateCount <= 1){
                midiValue = 0;
            }else{
                midiValue = (int)Math.round(currentValue * 127.0 / (stateCount-1));
            }
            onValueChange.accept(midiValue);
        }
    }

    public void setOnValueChange(Consumer<Integer> callback) {
        this.onValueChange = callback;
    }

    public void setValueSilently(int value) {
        suppressEvents = true;
        setValue(value);
        suppressEvents = false;
    }

    /* =========================
           Getters, setters, config
       =========================*/

    public void configure(FieldDefinition field) {

        Object labelsObject = field.getMapping().get("labels");
        if (labelsObject instanceof Map) {
            this.labels =
                    (Map<String, String>) labelsObject;
            this.stateCount = labels.size();
        }
        repaint();
    }

    public void setValue(int midiValue) {

        if (stateCount <= 1) {
            currentValue = 0;
        } else {
            currentValue =(int) Math.round(midiValue * (stateCount - 1) / 127.0);
        }
        repaint();
    }

    public int getValue() {
        return currentValue;
    }

    public void setActive (boolean active){
        this.active = active;
        repaint();
    }
}