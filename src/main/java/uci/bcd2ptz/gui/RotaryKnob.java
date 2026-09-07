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

public class RotaryKnob extends JComponent {

    //TODO Integracja z CORE i VISCA
    private int minValue;
    private int maxValue;
    private int currentValue;
    private boolean centeredRange;

    private Map<String, String> labels;

    //API
    private boolean suppressEvents;
    private boolean active = true;
    private boolean dragging = false;
    private int dragStartX;
    private int dragStartValue;
    private static final int DEAD_ZONE = 10;
    private static final int PIXELS_PER_MIDI_STEP = 3;

    private Consumer<Integer> onValueChange;

    public RotaryKnob() {

        setPreferredSize(new Dimension(80, 80));
        setOpaque(false);
        this.suppressEvents = false;

        addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                if (!active){
                    return;
                }
                dragging = true;
                dragStartX = e.getX();
                dragStartValue = currentValue;
            }

            @Override
            public void mouseReleased(MouseEvent e) {

                if (!active){
                    return;
                }

                dragging = false;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {

            @Override
            public void mouseDragged(MouseEvent e) {

                if (!active){
                    return;
                }

                if (!dragging) {
                    return;
                }

                int deltaX = e.getX() - dragStartX;

                if (Math.abs(deltaX) <= DEAD_ZONE) {
                    return;
                }

                int effectiveDelta = deltaX > 0 ? deltaX - DEAD_ZONE : deltaX + DEAD_ZONE;
                int midiDelta = effectiveDelta / PIXELS_PER_MIDI_STEP;

                int newValue = dragStartValue + midiDelta;

                if (newValue < 0){
                    newValue = 0;
                }

                if (newValue > 127){
                    newValue = 127;
                }

                if ((currentValue == 0 && newValue == 0) || (currentValue == 127 && newValue == 127)){
                    return;
                }

                if(newValue != currentValue){
                    currentValue = newValue;
                    repaint();
                    fireValueChange();
                }
            }
        });
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

        int centerX = w / 2;
        int centerY = h / 2;

        int radius = Math.min(w, h) / 2 - 12;

        // ===== SCALE =====

        int steps = maxValue - minValue + 1;
        int bucket = (int)Math.floor(currentValue * steps /128.0);
        if (bucket >= steps) {
            bucket = steps -1;
        }

        int logicalSteps = minValue + bucket;
        double normalized = (logicalSteps - minValue) / (double) (maxValue - minValue);

        int highlightTick;

        if (minValue < 0 && maxValue > 0) {
            // zakres symetryczny wokół zera
            highlightTick = -minValue;
        } else {
            // zakres jednostronny
            highlightTick = -1;
        }


        for (int i = 0; i < steps; i++) {

            double angleDeg = -150.0 + (i * (300.0 / (steps - 1)));
            double angle = Math.toRadians(angleDeg);
            boolean highlighted;
            if (highlightTick >= 0) {
                highlighted = (i == highlightTick);
            } else {
                highlighted = ((i == 0 ) || (i == steps -1));
            }
            int outer = radius + 4;
            int inner = highlighted ? radius - 14 : radius - 8;

            int x1 = centerX + (int) (Math.sin(angle) * inner);
            int y1 = centerY - (int) (Math.cos(angle) * inner);
            int x2 = centerX + (int) (Math.sin(angle) * outer);
            int y2 = centerY - (int) (Math.cos(angle) * outer);

            g2.setColor(new Color(160, 160, 160));
            g2.setStroke(new BasicStroke(highlighted? 3.0f : 1.0f));

            g2.drawLine(x1, y1, x2, y2);
        }

        if (labels != null && !labels.isEmpty()) {

            //Debug
            System.out.print("LABELS");

            g2.setColor(new Color(220, 220, 220));
            g2.setFont(g2.getFont().deriveFont(9f));

            int middleIndex  = steps / 2;

            drawScaleLabel(g2, centerX, centerY, radius, 0, labels.get("0"));
            drawScaleLabel(g2, centerX, centerY, radius,  middleIndex, labels.get( String.valueOf(middleIndex)));
            drawScaleLabel(g2, centerX, centerY, radius, steps - 1, labels.get(String.valueOf(steps - 1)));
        }

        // ===== KNOB BODY =====

        Shape knob = new Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2, radius * 2);

        g2.setColor(new Color(90, 90, 90));
        g2.fill(knob);

        g2.setColor(new Color(20, 20, 20));
        g2.setStroke(new BasicStroke(2f));

        g2.draw(knob);

        // ===== POINTER =====

        double angleDeg = -150.0 + normalized * 300.0;
        double angle = Math.toRadians(angleDeg);
        int pointerLength = radius - 5;
        int pointerWidth = 4;

        int x = centerX + (int) (Math.sin(angle) * pointerLength);
        int y = centerY - (int) (Math.cos(angle) * pointerLength);

        Color pointerColor = active ? new Color(255, 200, 40) : new Color(140, 140, 140);

        g2.setStroke(new BasicStroke(pointerWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(pointerColor);

        g2.drawLine(centerX, centerY, x, y);

        // ===== CENTER CAP =====

        g2.setColor(pointerColor);
        g2.fillOval(centerX - 5, centerY - 5, 10, 10);

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
            onValueChange.accept(currentValue);
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

        this.minValue = (int) field.getMapping().get("mappedMin");
        this.maxValue = (int) field.getMapping().get("mappedMax");

        Object labelsObject = field.getMapping().get("labels");

        if (labels instanceof Map){
            this.labels = (Map<String, String>) labelsObject;
        }
        //konfiguracja zakresu symetryczny/asymetryczny
        centeredRange = (minValue < 0 && maxValue > 0);

        repaint();
    }

    private void drawScaleLabel(Graphics2D g2, int centerX, int centerY, int radius, int step, String text) {

        if (text == null) {
            return;
        }

        int steps = maxValue - minValue + 1;

        double angleDeg = -150.0 + (step * (300.0 / (steps - 1)));

        double angle =  Math.toRadians(angleDeg);

        int labelRadius = radius + 5;

        int x = centerX + (int)(Math.sin(angle) * labelRadius);
        int y = centerY - (int)(Math.cos(angle) * labelRadius);

        FontMetrics fm = g2.getFontMetrics();


        int textWidth = fm.stringWidth(text);
        g2.setColor(Color.WHITE);
        //g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f);

        g2.drawString(text, x - textWidth / 2, y + 4);
    }

    public void setActive (boolean active){
        this.active = active;
        repaint();
    }

    public void setValue(int value) {

        value = Math.max(0, Math.min(127, value));
        this.currentValue = value;
        repaint();
    }

    public boolean isActive(){
        return this.active;
    }

    public int getValue() {
        return currentValue;
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }
}

