package uci.midi2ptz.gui;

import javax.swing.*;
import java.awt.*;

/**
 * Simple application splash screen displayed during startup.
 *
 * <p>The splash screen is shown while application subsystems are initialized and hidden once startup is complete.</p>
 *
 * <p>The implementation uses a lightweight {@link JWindow} without window decorations.</p>
 */

public class SplashScreen {

    private static JWindow window;

    /**
     * Creates splash screen user interface components.
     *
     * <p>The window is centered on screen and initially hidden. Display is controlled using {@link #show()} and {@link #hide()}.</p>
     */

    public SplashScreen() {

        window = new JWindow();

        JPanel panel = new JPanel(new BorderLayout());

        panel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        JLabel title =new JLabel("MIDI2PTZ",SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,28));
        JLabel status =new JLabel("Initializing...", SwingConstants.CENTER);

        panel.add(title,BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);

        window.setContentPane(panel);
        window.setSize(400, 220);
        window.setLocationRelativeTo(null);
    }

    /**
     * Displays the splash screen.
     * <p>The operation is executed on the Swing Event Dispatch Thread.</p>
     */
    public static void show() {
        SwingUtilities.invokeLater(() -> window.setVisible(true));
    }

    /**
     * Closes and disposes the splash screen window.
     * <p>The operation is executed on the Swing Event Dispatch Thread.</p>
     */
    public void hide() {
        SwingUtilities.invokeLater(window::dispose);
    }
}