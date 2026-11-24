package controller;

import java.awt.Component;
import java.util.List;
import java.util.Objects;
import javax.swing.JOptionPane;
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
 * Glue between the views and the schedulers: reads the input, runs the chosen scheduler,
 * and hands the result to the output panels. Contains no scheduling logic.
 */
public class SimulationController {

    /** Quantum values on the graph's x-axis: 1..10. */
    private static final int MAX_QUANTUM = 10;
    /** One graph line per context-switch value. */
    private static final int[] GRAPH_CONTEXT_SWITCHES = {0, 1, 2, 3};

    private final InputPanel inputPanel;
    private final GanttChartPanel ganttPanel;
    private final ResultsPanel resultsPanel;
    private final TierStatePanel tierStatePanel;
    private final GraphPanel graphPanel;
    private final Runnable showGraphTab;
    private final Component dialogParent;

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
        this.dialogParent = dialogParent;   // may be null: dialogs are then centred on the screen

        inputPanel.addRunListener(e -> runSimulation());
        inputPanel.addGraphListener(e -> buildGraph());
    }

    // ------------------------------------------------------------------ single run

    private void runSimulation() {
        List<Process> processes;
        int quantum;
        int contextSwitch;
        InputPanel.Algorithm algorithm;

        // Step 1: read and validate. A mistake here is the user's, so show it as a warning.
        try {
            processes = inputPanel.readProcesses();
            quantum = inputPanel.getQuantum();
            contextSwitch = inputPanel.getContextSwitch();
            algorithm = inputPanel.getAlgorithm();
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
            return;
        }

        // Step 2: simulate. A failure here is a program error, so clear old output and report it loudly.
        try {
            Scheduler scheduler = createScheduler(algorithm);
            // Scheduler.run takes (processes, contextSwitch, quantum), in that order.
            SimulationResult result = scheduler.run(processes, contextSwitch, quantum);

            ganttPanel.setEntries(result.getGantt());
            resultsPanel.setResult(result);
            tierStatePanel.setResult(result, algorithm == InputPanel.Algorithm.ACS);
        } catch (RuntimeException e) {
            e.printStackTrace();
            ganttPanel.clear();
            resultsPanel.clear();
            tierStatePanel.clear();
            showError(e);
        }
    }

    // --------------------------------------------------------------- Q / CS sweep

    /** Runs ACS for every (CS, Q) pair on the current processes and draws the two charts. */
    private void buildGraph() {
        List<Process> processes;
        try {
            processes = inputPanel.readProcesses();
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
            return;
        }

        try {
            int[] quantums = new int[MAX_QUANTUM];
            for (int i = 0; i < MAX_QUANTUM; i++) {
                quantums[i] = i + 1;
            }
            double[][] waiting = new double[GRAPH_CONTEXT_SWITCHES.length][MAX_QUANTUM];
            double[][] turnaround = new double[GRAPH_CONTEXT_SWITCHES.length][MAX_QUANTUM];

            for (int c = 0; c < GRAPH_CONTEXT_SWITCHES.length; c++) {
                for (int q = 0; q < MAX_QUANTUM; q++) {
                    // The scheduler copies and resets the processes, so the same list can be reused.
                    SimulationResult result =
                            new ACScheduler().run(processes, GRAPH_CONTEXT_SWITCHES[c], quantums[q]);
                    waiting[c][q] = result.getAvgWaiting();
                    turnaround[c][q] = result.getAvgTurnaround();
                }
            }

            graphPanel.setData(quantums, GRAPH_CONTEXT_SWITCHES, waiting, turnaround);
            showGraphTab.run();
        } catch (RuntimeException e) {
            e.printStackTrace();
            graphPanel.clear();
            showError(e);
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