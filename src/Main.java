import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import view.MainFrame;

/** Entry point: starts the Swing GUI on the event dispatch thread. */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Not fatal: Swing keeps its default look. We still report it instead of hiding
                // it.
                System.err.println("Could not apply the system look and feel, using the default: " + e);
            }
            new MainFrame().setVisible(true);
        });
    }
}