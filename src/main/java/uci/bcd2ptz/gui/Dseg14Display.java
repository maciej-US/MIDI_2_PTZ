package uci.bcd2ptz.gui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Objects;

public class Dseg14Display extends JPanel {

    private final JLabel backgroundLabel;
    private final JLabel middleLabel;
    private final JLabel foregroundLabel;

    public Dseg14Display(String fontPath) {

        setLayout(new OverlayLayout(this));

        setBackground(new Color(30,30,30));
        setOpaque(true);

        Font displayFont;

        try {
            displayFont = Font.createFont(Font.TRUETYPE_FONT, new File(fontPath)).deriveFont(22f);
        } catch (Exception e) {
            displayFont =new Font(Font.MONOSPACED,Font.BOLD,22);
        }

        backgroundLabel = new JLabel("O");
        backgroundLabel.setAlignmentX(0.5f);
        backgroundLabel.setAlignmentY(0.5f);
        backgroundLabel.setHorizontalAlignment(SwingConstants.CENTER);
        backgroundLabel.setForeground(new Color(15,15,15));

        middleLabel = new JLabel("*");
        middleLabel.setAlignmentX(0.5f);
        middleLabel.setAlignmentY(0.5f);
        middleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        middleLabel.setForeground(new Color(15,15,15));

        backgroundLabel.setFont(displayFont);
        middleLabel.setFont(displayFont);

        foregroundLabel = new JLabel(" ");
        foregroundLabel.setAlignmentX(0.5f);
        foregroundLabel.setAlignmentY(0.5f);
        foregroundLabel.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        foregroundLabel.setForeground(new Color(0xFFBF00));
        foregroundLabel.setFont(displayFont);

        add(foregroundLabel);
        add(middleLabel);
        add(backgroundLabel);
    }

    public void setValue(String value) {

        if (value == null || value.isBlank()) {

            clear();
            return;
        }

        foregroundLabel.setText(value.toUpperCase()
        );
    }

    public void clear() {

        foregroundLabel.setText(" ");
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(35, 40);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(35, 40);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(35, 40);
    }

}