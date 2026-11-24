package view;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import model.GanttEntry;

/** Draws the Gantt chart: one coloured block per process run, hatched grey for context switches. */
public class GanttChartPanel extends JPanel {

    private final Canvas canvas = new Canvas();

    public GanttChartPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Gantt chart"));
        add(new JScrollPane(canvas,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
    }

    public void setEntries(List<GanttEntry> entries) {
        canvas.setEntries(entries);
    }

    public void clear() {
        canvas.setEntries(Collections.<GanttEntry>emptyList());
    }

    /** The drawing surface. Its preferred width grows with the simulation length, so the scroll pane works. */
    private static final class Canvas extends JPanel {

        private static final int UNIT = 32;        // pixels per time unit
        private static final int MARGIN = 15;
        private static final int BAR_HEIGHT = 44;
        private static final int LABEL_SPACE = 22;
        private static final int HEIGHT = MARGIN + BAR_HEIGHT + LABEL_SPACE;

        private List<GanttEntry> entries = Collections.emptyList();
        private final Map<String, Color> colors = new HashMap<>();

        Canvas() {
            setPreferredSize(new Dimension(400, HEIGHT));
        }

        void setEntries(List<GanttEntry> newEntries) {
            entries = new ArrayList<>(Objects.requireNonNull(newEntries, "entries"));
            assignColors();
            int end = entries.isEmpty() ? 0 : entries.get(entries.size() - 1).getEnd();
            setPreferredSize(new Dimension(Math.max(400, 2 * MARGIN + end * UNIT), HEIGHT));
            revalidate();
            repaint();
        }

        /** Each process gets a fixed colour, spread evenly around the colour wheel. */
        private void assignColors() {
            colors.clear();
            int index = 0;
            for (GanttEntry entry : entries) {
                if (entry.getType() == GanttEntry.Type.PROCESS && !colors.containsKey(entry.getLabel())) {
                    colors.put(entry.getLabel(), Color.getHSBColor((index * 0.618f) % 1f, 0.45f, 0.90f));
                    index++;
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                FontMetrics fm = g2.getFontMetrics();

                if (entries.isEmpty()) {
                    g2.setColor(Color.GRAY);
                    g2.drawString("Run a simulation to see the Gantt chart.", MARGIN, MARGIN + BAR_HEIGHT / 2);
                    return;
                }

                for (GanttEntry entry : entries) {
                    int x = MARGIN + entry.getStart() * UNIT;
                    int width = entry.getDuration() * UNIT;
                    paintBlock(g2, fm, entry, x, width);

                    g2.setColor(getForeground());
                    g2.drawString(String.valueOf(entry.getStart()), x - 3, MARGIN + BAR_HEIGHT + 16);
                }
                GanttEntry last = entries.get(entries.size() - 1);
                g2.setColor(getForeground());
                g2.drawString(String.valueOf(last.getEnd()),
                        MARGIN + last.getEnd() * UNIT - 3, MARGIN + BAR_HEIGHT + 16);
            } finally {
                g2.dispose();
            }
        }

        private void paintBlock(Graphics2D g2, FontMetrics fm, GanttEntry entry, int x, int width) {
            Color fill;
            switch (entry.getType()) {
                case PROCESS:
                    fill = colors.get(entry.getLabel());
                    break;
                case CONTEXT_SWITCH:
                    fill = new Color(170, 170, 170);
                    break;
                case IDLE:
                    fill = new Color(235, 235, 235);
                    break;
                default:
                    throw new IllegalStateException("Unknown Gantt type: " + entry.getType());
            }
            g2.setColor(fill);
            g2.fillRect(x, MARGIN, width, BAR_HEIGHT);

            if (entry.getType() == GanttEntry.Type.CONTEXT_SWITCH) {
                g2.setColor(new Color(110, 110, 110));
                for (int hx = x - BAR_HEIGHT; hx < x + width; hx += 8) {   // diagonal hatching, clipped to the block
                    java.awt.Shape oldClip = g2.getClip();
                    g2.clipRect(x, MARGIN, width, BAR_HEIGHT);
                    g2.drawLine(hx, MARGIN + BAR_HEIGHT, hx + BAR_HEIGHT, MARGIN);
                    g2.setClip(oldClip);
                }
            }

            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(x, MARGIN, width, BAR_HEIGHT);

            String label = entry.getLabel();
            int textWidth = fm.stringWidth(label);
            if (textWidth + 4 <= width) {   // skip the text if the block is too narrow to hold it
                g2.setColor(Color.BLACK);
                g2.drawString(label, x + (width - textWidth) / 2,
                        MARGIN + (BAR_HEIGHT + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
    }
}