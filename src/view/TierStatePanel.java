package view;

import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import model.SimulationResult;
import model.TimeStepSnapshot;

/** One row per time step: running process and the contents of each queue (the "demotion matrix"). */
public class TierStatePanel extends JPanel {

    private static final String[] TIERED_COLUMNS = {"Time", "Running", "Tier 1 (Round Robin)", "Tier 2 (Priority + aging)"};
    private static final String[] SJF_COLUMNS = {"Time", "Running", "Ready queue (waiting)"};

    /** Queue text can be long, so hovering a cell shows its full content. */
    private final JTable table = new JTable() {
        @Override
        public String getToolTipText(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            int col = columnAtPoint(event.getPoint());
            if (row < 0 || col < 0) {
                return null;
            }
            Object value = getValueAt(row, col);
            return value == null ? null : value.toString();
        }
    };

    public TierStatePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Queue state at each time step"));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        clear();
    }

    /**
     * @param tiered true for ACS (two tiers), false for SJF (one ready queue)
     */
    public void setResult(SimulationResult result, boolean tiered) {
        DefaultTableModel model = newModel(tiered ? TIERED_COLUMNS : SJF_COLUMNS);
        for (TimeStepSnapshot step : result.getSteps()) {
            if (tiered) {
                model.addRow(new Object[]{step.getTime(), step.getRunning(),
                        format(step.getTier1()), format(step.getTier2())});
            } else {
                model.addRow(new Object[]{step.getTime(), step.getRunning(), format(step.getTier1())});
            }
        }
        table.setModel(model);   // rebuilds the columns for the new model
        int[] widths = tiered ? new int[]{55, 80, 430, 430} : new int[]{55, 80, 600};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    public void clear() {
        table.setModel(newModel(TIERED_COLUMNS));
    }

    private static DefaultTableModel newModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static String format(List<String> queue) {
        return queue.isEmpty() ? "[Empty]" : String.join(", ", queue);
    }
}