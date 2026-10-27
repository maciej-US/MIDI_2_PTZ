package uci.midi2ptz.gui;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;

/**
 * 14-segment style display component based on the DSEG14 font family.
 *
 * <p>The component emulates a hardware LED/VFD display commonly found on broadcast equipment, MIDI controllers and PTZ control surfaces.</p>
 *
 * <p>The display renders three visual layers:</p> *
 * <ul>
 *     <li>a background segment mask,</li>
 *     <li>a dim inactive segment layer,</li>
 *     <li>an illuminated foreground value.</li>
 * </ul>
 *
 * <p>This approach creates the appearance of a physical segmented display with inactive and active segments visible simultaneously.</p>
 */

public class Dseg14Display extends JPanel {

    /**
     * Active display layer containing the current value.
     */
    private final JLabel foregroundLabel;


    /**
     * Creates a segmented display component.
     *
     * <p>The constructor loads the bundled DSEG14 font when available and automatically falls back to a monospaced system font if the font resource
     * cannot be loaded.</p>
     */
    public Dseg14Display() {

        setLayout(new OverlayLayout(this));

        setBackground(new Color(30,30,30));
        setOpaque(true);

        Font displayFont;

        try {
            InputStream fontStream = getClass().getResourceAsStream("/gui/DSEG14Classic-Bold.ttf");
            displayFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(22f);
        } catch (Exception e) {
            displayFont =new Font(Font.MONOSPACED,Font.BOLD,22);
        }

        JLabel backgroundLabel = new JLabel("O");
        backgroundLabel.setAlignmentX(0.5f);
        backgroundLabel.setAlignmentY(0.5f);
        backgroundLabel.setHorizontalAlignment(SwingConstants.CENTER);
        backgroundLabel.setForeground(new Color(15,15,15));

        JLabel middleLabel = new JLabel("*");
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

    /**
     * Updates the displayed value.
     *
     * <p>The supplied value is automatically converted to upper case before being rendered.</p>
     *
     * @param value value to display
     */
    public void setValue(String value) {

        if (value == null || value.isBlank()) {
            clear();
            return;
        }
        foregroundLabel.setText(value.toUpperCase()
        );
    }

    /**
     * Clears the display contents.
     */
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