package view;

import java.awt.BorderLayout;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import model.Process;
import model.SimulationResult;

/** Per-process waiting, turnaround and response time, plus the averages. */
public class ResultsPanel extends JPanel {

    private static final String[] COLUMNS =
            {"Process", "Arrival", "Burst", "Finish", "Turnaround", "Waiting", "Response"};

    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;   // results are read-only
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JLabel summary = new JLabel(" ");

    public ResultsPanel() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createTitledBorder("Results"));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultRenderer(Object.class, center);

        add(new JScrollPane(table), BorderLayout.CENTER);
        summary.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        add(summary, BorderLayout.SOUTH);
    }

    public void setResult(SimulationResult result) {
        tableModel.setRowCount(0);
        for (Process p : result.getProcesses()) {
            tableModel.addRow(new Object[]{
                    p.getName(), p.getArrival(), p.getBurst(), p.getFinishTime(),
                    p.getTurnaroundTime(), p.getWaitingTime(), p.getResponseTime()});
        }
        // Locale.US keeps ASCII digits and a "." decimal point on every computer.
        summary.setText(String.format(Locale.US,
                "<html>Avg waiting: <b>%.2f</b> &nbsp;|&nbsp; Avg turnaround: <b>%.2f</b> &nbsp;|&nbsp; "
                        + "Avg response: <b>%.2f</b> &nbsp;|&nbsp; CPU utilization: <b>%.1f%%</b> &nbsp;|&nbsp; "
                        + "Total time: <b>%d</b></html>",
                result.getAvgWaiting(), result.getAvgTurnaround(), result.getAvgResponse(),
                result.getCpuUtilization() * 100.0, result.getTotalTime()));
    }

    public void clear() {
        tableModel.setRowCount(0);
        summary.setText(" ");
    }
}