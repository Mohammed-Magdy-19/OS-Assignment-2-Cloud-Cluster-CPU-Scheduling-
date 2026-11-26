package controller;

import java.awt.Component;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import model.Process;
import model.SimulationResult;
import scheduler.ACScheduler;
import scheduler.PreemptiveSJF;
import scheduler.Scheduler;
import view.GanttChartPanel;
import view.GraphPanel;
import view.InputPanel;
import view.ResultsPanel;
import view.TierStatePanel;

/**
 * Glue between the views and the schedulers: reads the input, runs the chosen
 * scheduler,
 * and hands the result to the output panels. Contains no scheduling logic.
 */
public class SimulationController {

    /** Default upper bound for quantum values on the graph's x-axis. */
    private static final int DEFAULT_MAX_QUANTUM = 10;
    /** One graph line per context-switch value. */
    private static final int[] GRAPH_CONTEXT_SWITCHES = { 0, 1, 2, 3 };

    private final InputPanel inputPanel;
    private final GanttChartPanel ganttPanel;
    private final ResultsPanel resultsPanel;
    private final TierStatePanel tierStatePanel;
    private final GraphPanel graphPanel;
    private final Runnable showGraphTab;
    private final Component dialogParent;

    /**
     * Debounce timer: when the input changes rapidly (e.g. typing in a cell),
     * we wait 400 ms of quiet before rebuilding the graph.
     */
    private final Timer graphDebounceTimer;
    /** Generation counter so stale background sweeps don't overwrite newer results. */
    private final AtomicInteger graphGeneration = new AtomicInteger();

    public SimulationController(InputPanel inputPanel,
            GanttChartPanel ganttPanel,
            ResultsPanel resultsPanel,
            TierStatePanel tierStatePanel,
            GraphPanel graphPanel,
            Runnable showGraphTab,
            Component dialogParent) {
        this.inputPanel = Objects.requireNonNull(inputPanel, "inputPanel");
        this.ganttPanel = Objects.requireNonNull(ganttPanel, "ganttPanel");
        this.resultsPanel = Objects.requireNonNull(resultsPanel, "resultsPanel");
        this.tierStatePanel = Objects.requireNonNull(tierStatePanel, "tierStatePanel");
        this.graphPanel = Objects.requireNonNull(graphPanel, "graphPanel");
        this.showGraphTab = Objects.requireNonNull(showGraphTab, "showGraphTab");
        this.dialogParent = dialogParent; // may be null: dialogs are then centred on the screen

        inputPanel.addRunListener(e -> runSimulation());
        inputPanel.addGraphListener(e -> buildGraph());

        // Debounced auto-rebuild: restart the 400 ms timer on every input change.
        graphDebounceTimer = new Timer(400, e -> rebuildGraphFromInput());
        graphDebounceTimer.setRepeats(false);
        inputPanel.addInputChangeListener(() -> graphDebounceTimer.restart());
    }

    // ------------------------------------------------------------------ single run

    private void runSimulation() {
        List<Process> processes;
        int quantum;
        int contextSwitch;
        InputPanel.Algorithm algorithm;

        // Step 1: read and validate. A mistake here is the user's, so show it as a
        // warning.
        try {
            processes = inputPanel.readProcesses();
            quantum = inputPanel.getQuantum();
            contextSwitch = inputPanel.getContextSwitch();
            algorithm = inputPanel.getAlgorithm();
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
            return;
        }

        // Step 2: simulate. A failure here is a program error, so clear old output and
        // report it loudly.
        try {
            Scheduler scheduler = createScheduler(algorithm);
            // Scheduler.run takes (processes, contextSwitch, quantum), in that order.
            SimulationResult result = scheduler.run(processes, contextSwitch, quantum);

            ganttPanel.setEntries(result.getGantt());
            resultsPanel.setResult(result);
            tierStatePanel.setResult(result, algorithm == InputPanel.Algorithm.ACS);

            // Automatically refresh the Q/CS graph so it always reflects the
            // current process set. Run on a background thread to keep the UI
            // responsive (the sweep runs many simulations).
            buildGraphQuietly(processes);
        } catch (RuntimeException e) {
            e.printStackTrace();
            ganttPanel.clear();
            resultsPanel.clear();
            tierStatePanel.clear();
            showError(e);
        }
    }

    // --------------------------------------------------------------- Q / CS sweep

    /**
     * Runs ACS for every (CS, Q) pair on the current processes and draws the two
     * charts. Called from the "Show Q/CS graph" button.
     */
    private void buildGraph() {
        List<Process> processes;
        try {
            processes = inputPanel.readProcesses();
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
            return;
        }
        final int gen = graphGeneration.incrementAndGet();
        buildGraphCore(processes, true, gen);
    }

    /**
     * Called by the debounce timer when input changes.
     * Reads the current processes and silently rebuilds the graph.
     */
    private void rebuildGraphFromInput() {
        List<Process> processes;
        try {
            processes = inputPanel.readProcesses();
        } catch (IllegalArgumentException ignored) {
            // Input is currently invalid (e.g. empty cell mid-typing); skip.
            return;
        }
        buildGraphQuietly(processes);
    }

    /**
     * Silently rebuild the graph on a background thread.
     * Uses a generation counter so that if a newer rebuild starts before this
     * one finishes, the stale result is discarded.
     */
    private void buildGraphQuietly(List<Process> processes) {
        final int gen = graphGeneration.incrementAndGet();
        new javax.swing.SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                buildGraphCore(processes, false, gen);
                return null;
            }
        }.execute();
    }

    /**
     * Core sweep: runs ACS for every (CS, Q) combination and pushes the results
     * into the graph panel.
     *
     * @param processes    the process list to sweep over
     * @param showOnError  if true, show a dialog on failure; if false, fail silently
     * @param generation   generation counter; result is discarded if superseded
     */
    private void buildGraphCore(List<Process> processes, boolean showOnError, int generation) {
        try {
            int currentQ = 1;
            try {
                currentQ = inputPanel.getQuantum();
            } catch (IllegalArgumentException ignored) {
                // spinner might be invalid; use defaults
            }
            int maxQ = Math.max(DEFAULT_MAX_QUANTUM, currentQ + 2);
            int[] quantums = new int[maxQ];
            for (int i = 0; i < maxQ; i++) {
                quantums[i] = i + 1;
            }
            double[][] waiting = new double[GRAPH_CONTEXT_SWITCHES.length][maxQ];
            double[][] turnaround = new double[GRAPH_CONTEXT_SWITCHES.length][maxQ];

            for (int c = 0; c < GRAPH_CONTEXT_SWITCHES.length; c++) {
                for (int q = 0; q < maxQ; q++) {
                    SimulationResult result =
                            new ACScheduler().run(processes, GRAPH_CONTEXT_SWITCHES[c], quantums[q]);
                    waiting[c][q] = result.getAvgWaiting();
                    turnaround[c][q] = result.getAvgTurnaround();
                }
            }

            // Discard if a newer rebuild has started since this one began.
            if (graphGeneration.get() != generation) {
                return;
            }

            // Update the UI on the EDT.
            final int[] fQuantums = quantums;
            final double[][] fWaiting = waiting;
            final double[][] fTurnaround = turnaround;
            javax.swing.SwingUtilities.invokeLater(() -> {
                // Double-check generation on the EDT too.
                if (graphGeneration.get() != generation) {
                    return;
                }
                graphPanel.setData(fQuantums, GRAPH_CONTEXT_SWITCHES, fWaiting, fTurnaround);
                if (showOnError) {
                    showGraphTab.run();
                }
            });
        } catch (RuntimeException e) {
            e.printStackTrace();
            javax.swing.SwingUtilities.invokeLater(() -> graphPanel.clear());
            if (showOnError) {
                javax.swing.SwingUtilities.invokeLater(() -> showError(e));
            }
        }
    }

    // --------------------------------------------------------------------- helpers

    private static Scheduler createScheduler(InputPanel.Algorithm algorithm) {
        switch (algorithm) {
            case PREEMPTIVE_SJF:
                return new PreemptiveSJF();
            case ACS:
                return new ACScheduler();
            default:
                throw new IllegalStateException("Unknown algorithm: " + algorithm);
        }
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(dialogParent, message, "Invalid input", JOptionPane.WARNING_MESSAGE);
    }

    private void showError(RuntimeException e) {
        JOptionPane.showMessageDialog(dialogParent, "The simulation failed:\n" + e,
                "Simulation error", JOptionPane.ERROR_MESSAGE);
    }
}