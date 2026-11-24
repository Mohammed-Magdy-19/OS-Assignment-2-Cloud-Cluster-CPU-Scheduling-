package view;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Two line charts (average waiting time and average turnaround time) against the quantum,
 * with one line per context-switch value. It only draws numbers it is given;
 * the controller does the simulations.
 */
public class GraphPanel extends JPanel {

    private static final Color[] COLORS = {
            new Color(31, 119, 180), new Color(214, 39, 40), new Color(44, 160, 44),
            new Color(255, 127, 14), new Color(148, 103, 189), new Color(23, 190, 207)
    };

    private final ChartCanvas waitingChart = new ChartCanvas("Average waiting time");
    private final ChartCanvas turnaroundChart = new ChartCanvas("Average turnaround time");
    private final JPanel charts = new JPanel(new GridLayout(1, 2, 8, 8));
    private final JButton saveButton = new JButton("Save graphs as PNG...");

    public GraphPanel() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createTitledBorder("ACS: effect of Quantum (Q) and Context Switch (CS)"));

        charts.setBackground(Color.WHITE);
        charts.add(waitingChart);
        charts.add(turnaroundChart);
        add(charts, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(saveButton);
        add(bottom, BorderLayout.SOUTH);

        saveButton.setEnabled(false);   // nothing to save until data arrives
        saveButton.addActionListener(e -> saveAsPng());
    }

    /**
     * @param quantums        x-axis values, for example 1..10
     * @param contextSwitches one line per value, for example 0..3
     * @param avgWaiting      [contextSwitch index][quantum index]
     * @param avgTurnaround   [contextSwitch index][quantum index]
     * @throws IllegalArgumentException if the arrays are empty, mismatched, negative or not finite
     */
    public void setData(int[] quantums, int[] contextSwitches, double[][] avgWaiting, double[][] avgTurnaround) {
        if (quantums == null || quantums.length == 0) {
            throw new IllegalArgumentException("quantums must not be empty");
        }
        if (contextSwitches == null || contextSwitches.length == 0) {
            throw new IllegalArgumentException("contextSwitches must not be empty");
        }
        requireMatrix(avgWaiting, contextSwitches.length, quantums.length, "avgWaiting");
        requireMatrix(avgTurnaround, contextSwitches.length, quantums.length, "avgTurnaround");

        waitingChart.setData(quantums, contextSwitches, avgWaiting);
        turnaroundChart.setData(quantums, contextSwitches, avgTurnaround);
        saveButton.setEnabled(true);
    }

    public void clear() {
        waitingChart.clear();
        turnaroundChart.clear();
        saveButton.setEnabled(false);
    }

    private static void requireMatrix(double[][] m, int rows, int cols, String name) {
        if (m == null || m.length != rows) {
            throw new IllegalArgumentException(name + " must have " + rows + " rows");
        }
        for (double[] row : m) {
            if (row == null || row.length != cols) {
                throw new IllegalArgumentException(name + " rows must have " + cols + " values");
            }
            for (double v : row) {
                if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) {
                    throw new IllegalArgumentException(name + " contains an invalid value: " + v);
                }
            }
        }
    }

    // ------------------------------------------------------------------- PNG export

    private void saveAsPng() {
        int width = charts.getWidth();
        int height = charts.getHeight();
        if (width <= 0 || height <= 0) {
            JOptionPane.showMessageDialog(this, "The graphs are not visible yet.",
                    "Cannot save", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("q_cs_graph.png"));
        chooser.setFileFilter(new FileNameExtensionFilter("PNG image", "png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
            file = new File(file.getPath() + ".png");
        }
        if (file.exists() && JOptionPane.showConfirmDialog(this,
                file.getName() + " already exists. Overwrite it?", "Confirm overwrite",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);
            charts.paint(g2);
        } finally {
            g2.dispose();
        }

        try {
            if (!ImageIO.write(image, "png", file)) {
                throw new IOException("No PNG writer is available");
            }
            JOptionPane.showMessageDialog(this, "Saved " + file.getAbsolutePath(),
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save the image:\n" + e.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --------------------------------------------------------------------- one chart

    private static final class ChartCanvas extends JPanel {

        private static final int LEFT = 60, RIGHT = 20, TOP = 34, BOTTOM = 62;
        private static final int MARKER = 7;

        private final String title;
        private int[] quantums = new int[0];
        private int[] contextSwitches = new int[0];
        private double[][] values = new double[0][0];
        private double yMax = 1;
        private double yStep = 1;

        ChartCanvas(String title) {
            this.title = title;
            setBackground(Color.WHITE);                 // white so exported images look clean
            setPreferredSize(new Dimension(380, 320));
            setToolTipText("");                         // enables getToolTipText below
        }

        void setData(int[] q, int[] cs, double[][] v) {
            quantums = q.clone();
            contextSwitches = cs.clone();
            values = new double[v.length][];
            double max = 0;
            for (int i = 0; i < v.length; i++) {
                values[i] = v[i].clone();
                for (double x : values[i]) {
                    max = Math.max(max, x);
                }
            }
            if (max <= 0) {
                yStep = 1;
                yMax = 1;
            } else {
                yStep = niceStep(max / 5);
                yMax = Math.ceil(max / yStep - 1e-9) * yStep;
            }
            repaint();
        }

        void clear() {
            quantums = new int[0];
            contextSwitches = new int[0];
            values = new double[0][0];
            repaint();
        }

        /** Rounds a raw step up to 1, 2, 5 or 10 times a power of ten, so the axis labels look tidy. */
        private static double niceStep(double raw) {
            double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
            double r = raw / magnitude;
            double factor = r <= 1 ? 1 : r <= 2 ? 2 : r <= 5 ? 5 : 10;
            return factor * magnitude;
        }

        // ---- geometry shared by painting and tooltips
        private int plotWidth()  { return Math.max(1, getWidth() - LEFT - RIGHT); }
        private int plotHeight() { return Math.max(1, getHeight() - TOP - BOTTOM); }

        private int xAt(int index) {
            int n = quantums.length;
            if (n == 1) {
                return LEFT + plotWidth() / 2;
            }
            return LEFT + (int) Math.round(index * (double) plotWidth() / (n - 1));
        }

        private int yAt(double value) {
            return TOP + plotHeight() - (int) Math.round(value / yMax * plotHeight());
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            for (int c = 0; c < values.length; c++) {
                for (int q = 0; q < quantums.length; q++) {
                    int dx = event.getX() - xAt(q);
                    int dy = event.getY() - yAt(values[c][q]);
                    if (dx * dx + dy * dy <= 100) {
                        return String.format(Locale.US, "CS = %d, Q = %d: %.2f",
                                contextSwitches[c], quantums[q], values[c][q]);
                    }
                }
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                FontMetrics fm = g2.getFontMetrics();

                if (quantums.length == 0) {
                    g2.setColor(Color.GRAY);
                    g2.drawString("Click \"Show Q/CS graph\" to generate the charts.", 20, getHeight() / 2);
                    return;
                }
                paintTitle(g2);
                paintAxes(g2, fm);
                paintLines(g2);
                paintLegend(g2, fm);
            } finally {
                g2.dispose();
            }
        }

        private void paintTitle(Graphics2D g2) {
            Font old = g2.getFont();
            g2.setFont(old.deriveFont(Font.BOLD, old.getSize2D() + 1f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Color.BLACK);
            g2.drawString(title, (getWidth() - fm.stringWidth(title)) / 2, 20);
            g2.setFont(old);
        }

        private void paintAxes(Graphics2D g2, FontMetrics fm) {
            int ticks = (int) Math.round(yMax / yStep);
            String format = yStep < 1 ? "%.1f" : "%.0f";
            for (int k = 0; k <= ticks; k++) {
                double value = k * yStep;
                int y = yAt(value);
                g2.setColor(new Color(225, 225, 225));
                g2.drawLine(LEFT, y, LEFT + plotWidth(), y);
                g2.setColor(Color.BLACK);
                String label = String.format(Locale.US, format, value);
                g2.drawString(label, LEFT - 8 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
            }

            g2.setColor(Color.BLACK);
            g2.drawLine(LEFT, TOP, LEFT, TOP + plotHeight());
            g2.drawLine(LEFT, TOP + plotHeight(), LEFT + plotWidth(), TOP + plotHeight());

            int every = Math.max(1, (quantums.length + 11) / 12);   // avoid overlapping x labels
            for (int i = 0; i < quantums.length; i += every) {
                String label = String.valueOf(quantums[i]);
                int x = xAt(i);
                g2.drawLine(x, TOP + plotHeight(), x, TOP + plotHeight() + 4);
                g2.drawString(label, x - fm.stringWidth(label) / 2, TOP + plotHeight() + 18);
            }

            String xTitle = "Quantum (Q)";
            g2.drawString(xTitle, LEFT + (plotWidth() - fm.stringWidth(xTitle)) / 2, TOP + plotHeight() + 34);

            AffineTransform saved = g2.getTransform();
            g2.translate(14, TOP + plotHeight() / 2 + fm.stringWidth("Time units") / 2);
            g2.rotate(-Math.PI / 2);
            g2.drawString("Time units", 0, 0);
            g2.setTransform(saved);
        }

        private void paintLines(Graphics2D g2) {
            g2.setStroke(new BasicStroke(2f));
            for (int c = 0; c < values.length; c++) {
                g2.setColor(COLORS[c % COLORS.length]);
                for (int q = 0; q + 1 < quantums.length; q++) {
                    g2.drawLine(xAt(q), yAt(values[c][q]), xAt(q + 1), yAt(values[c][q + 1]));
                }
                for (int q = 0; q < quantums.length; q++) {
                    g2.fillOval(xAt(q) - MARKER / 2, yAt(values[c][q]) - MARKER / 2, MARKER, MARKER);
                }
            }
            g2.setStroke(new BasicStroke(1f));
        }

        private void paintLegend(Graphics2D g2, FontMetrics fm) {
            int itemWidth = 70;
            int totalWidth = itemWidth * contextSwitches.length;
            int x = (getWidth() - totalWidth) / 2;
            int y = getHeight() - 12;
            for (int c = 0; c < contextSwitches.length; c++) {
                g2.setColor(COLORS[c % COLORS.length]);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(x, y - 4, x + 16, y - 4);
                g2.fillOval(x + 8 - MARKER / 2, y - 4 - MARKER / 2, MARKER, MARKER);
                g2.setColor(Color.BLACK);
                g2.drawString("CS = " + contextSwitches[c], x + 20, y);
                x += itemWidth;
            }
            g2.setStroke(new BasicStroke(1f));
        }
    }
}