package view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import model.Process;
import util.TestCaseLoader;
import util.TestCaseLoader.TestCase;

/**
 * Left-hand panel: process table, algorithm choice, quantum, context switch and the Run button.
 * It only reads and validates user input. It never runs a scheduler.
 */
public class InputPanel extends JPanel {

    public enum Algorithm {
        PREEMPTIVE_SJF("Preemptive SJF"),
        ACS("Adaptive Container Scheduler (ACS)");

        private final String label;

        Algorithm(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    /** Upper limit for every number, so a typo cannot freeze the simulator. */
    public static final int MAX_VALUE = 1000;

    private static final String[] COLUMNS = {"Process", "Arrival", "Burst", "Priority"};
    private static final int NAME_COL = 0, ARRIVAL_COL = 1, BURST_COL = 2, PRIORITY_COL = 3;

    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0);
    private final JTable table = new JTable(tableModel);
    private final JComboBox<Algorithm> algorithmBox = new JComboBox<>(Algorithm.values());
    private final JSpinner quantumSpinner = new JSpinner(new SpinnerNumberModel(3, 1, MAX_VALUE, 1));
    private final JSpinner contextSwitchSpinner = new JSpinner(new SpinnerNumberModel(0, 0, MAX_VALUE, 1));
    private final JButton runButton = new JButton("Run simulation");
    private final JButton graphButton = new JButton("Show Q/CS graph (ACS)");

    /** Listeners notified whenever any input field changes. */
    private final List<Runnable> inputChangeListeners = new CopyOnWriteArrayList<>();
    /** Suppresses change notifications during bulk operations like loadPreset. */
    private boolean suppressChangeEvents = false;

    public InputPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createTitledBorder("Input"));
        setPreferredSize(new Dimension(380, 0));

        // Commit a half-typed cell when focus moves away (for example to the Run button).
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(340, 180));
        add(tableScroll, BorderLayout.CENTER);

        JButton addButton = new JButton("Add process");
        JButton removeButton = new JButton("Remove selected");
        JButton test1Button = new JButton("Load Test 1");
        JButton test2Button = new JButton("Load Test 2");
        addButton.addActionListener(e -> addRow());
        removeButton.addActionListener(e -> removeSelectedRows());
        test1Button.addActionListener(e -> loadPreset(1));
        test2Button.addActionListener(e -> loadPreset(2));

        JPanel tableButtons = new JPanel(new GridLayout(2, 2, 6, 6));
        tableButtons.add(addButton);
        tableButtons.add(removeButton);
        tableButtons.add(test1Button);
        tableButtons.add(test2Button);

        JPanel settings = new JPanel(new GridLayout(3, 2, 6, 6));
        settings.add(new JLabel("Algorithm:"));
        settings.add(algorithmBox);
        settings.add(new JLabel("Quantum (ACS only):"));
        settings.add(quantumSpinner);
        settings.add(new JLabel("Context switch:"));
        settings.add(contextSwitchSpinner);

        // SJF has no quantum: disable the field instead of silently ignoring it.
        algorithmBox.setSelectedItem(Algorithm.ACS);
        algorithmBox.addActionListener(e -> updateQuantumEnabled());
        updateQuantumEnabled();

        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.add(tableButtons, BorderLayout.NORTH);
        south.add(settings, BorderLayout.CENTER);
        JPanel actionButtons = new JPanel(new GridLayout(2, 1, 0, 6));
        actionButtons.add(runButton);
        actionButtons.add(graphButton);
        south.add(actionButtons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        // --- wire up change listeners so the graph auto-updates on any input edit ---

        // Table cell edits, row inserts/deletes
        tableModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                fireInputChanged();
            }
        });

        // Spinner value changes
        quantumSpinner.addChangeListener(e -> fireInputChanged());
        contextSwitchSpinner.addChangeListener(e -> fireInputChanged());

        loadPreset(1);   // start with a ready-to-run example
    }

    // ------------------------------------------------------------------ public API

    public void addRunListener(ActionListener listener) {
        runButton.addActionListener(listener);
    }

    public void addGraphListener(ActionListener listener) {
        graphButton.addActionListener(listener);
    }

    /**
     * Register a callback that fires whenever any input changes:
     * table cell edits, row add/remove, preset loads, or spinner changes.
     */
    public void addInputChangeListener(Runnable listener) {
        inputChangeListeners.add(listener);
    }

    /**
     * Reads the table and builds the processes.
     *
     * @throws IllegalArgumentException with a readable message for any invalid cell
     */
    public List<Process> readProcesses() {
        stopEditing();
        int rows = tableModel.getRowCount();
        if (rows == 0) {
            throw new IllegalArgumentException("Add at least one process.");
        }
        List<Process> processes = new ArrayList<>(rows);
        Set<String> names = new HashSet<>();
        for (int row = 0; row < rows; row++) {
            String name = cell(row, NAME_COL);
            int arrival = readInt(row, ARRIVAL_COL);
            int burst = readInt(row, BURST_COL);
            int priority = readInt(row, PRIORITY_COL);
            Process process;
            try {
                process = new Process(name, arrival, burst, priority);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Row " + (row + 1) + ": " + e.getMessage());
            }
            if (!names.add(process.getName())) {
                throw new IllegalArgumentException(
                        "Row " + (row + 1) + ": duplicate process name \"" + process.getName() + "\"");
            }
            processes.add(process);
        }
        return processes;
    }

    public int getQuantum() {
        return readSpinner(quantumSpinner, "Quantum");
    }

    public int getContextSwitch() {
        return readSpinner(contextSwitchSpinner, "Context switch");
    }

    public Algorithm getAlgorithm() {
        Object selected = algorithmBox.getSelectedItem();
        if (!(selected instanceof Algorithm)) {
            throw new IllegalArgumentException("Choose an algorithm.");
        }
        return (Algorithm) selected;
    }

    // --------------------------------------------------------------------- helpers

    private void updateQuantumEnabled() {
        quantumSpinner.setEnabled(algorithmBox.getSelectedItem() == Algorithm.ACS);
    }

    private void stopEditing() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    private void addRow() {
        stopEditing();
        tableModel.addRow(new Object[]{nextFreeName(), 0, 1, 1});
    }

    private String nextFreeName() {
        Set<String> used = new HashSet<>();
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            used.add(cell(row, NAME_COL));
        }
        int k = 1;
        while (used.contains("P" + k)) {
            k++;
        }
        return "P" + k;
    }

    private void removeSelectedRows() {
        stopEditing();
        int[] selected = table.getSelectedRows();
        if (selected.length == 0) {
            JOptionPane.showMessageDialog(this, "Select one or more rows first.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        for (int i = selected.length - 1; i >= 0; i--) {   // remove from the bottom so indexes stay valid
            tableModel.removeRow(selected[i]);
        }
    }

    private void loadPreset(int number) {
        stopEditing();
        TestCase tc;
        try {
            tc = TestCaseLoader.load(number);
        } catch (IllegalArgumentException | java.io.UncheckedIOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Could not load test case", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Suppress individual change events during bulk load; fire one at the end.
        suppressChangeEvents = true;
        try {
            tableModel.setRowCount(0);
            for (Process p : tc.getProcesses()) {
                tableModel.addRow(new Object[]{p.getName(), p.getArrival(), p.getBurst(), p.getPriority()});
            }
            quantumSpinner.setValue(tc.getQuantum());
            contextSwitchSpinner.setValue(tc.getContextSwitch());
        } finally {
            suppressChangeEvents = false;
        }
        fireInputChanged();
    }

    private void fireInputChanged() {
        if (suppressChangeEvents) {
            return;
        }
        for (Runnable listener : inputChangeListeners) {
            listener.run();
        }
    }

    private String cell(int row, int col) {
        Object value = tableModel.getValueAt(row, col);
        return value == null ? "" : value.toString().trim();
    }

    private int readInt(int row, int col) {
        String text = cell(row, col);
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + (row + 1) + ": " + COLUMNS[col]
                    + " must be a whole number, got \"" + text + "\"");
        }
        if (value > MAX_VALUE) {
            throw new IllegalArgumentException("Row " + (row + 1) + ": " + COLUMNS[col]
                    + " must be at most " + MAX_VALUE + ", got " + value);
        }
        return value;
    }

    private static int readSpinner(JSpinner spinner, String label) {
        try {
            spinner.commitEdit();   // accept text that was typed but not confirmed yet
        } catch (ParseException e) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
        return ((Number) spinner.getValue()).intValue();
    }
}