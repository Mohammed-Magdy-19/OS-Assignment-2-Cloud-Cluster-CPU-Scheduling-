package view;

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import controller.SimulationController;

/** Main window: input on the left; Gantt chart on top and results / queue states / graphs below. */
public class MainFrame extends JFrame {

    private final InputPanel inputPanel = new InputPanel();
    private final GanttChartPanel ganttPanel = new GanttChartPanel();
    private final ResultsPanel resultsPanel = new ResultsPanel();
    private final TierStatePanel tierStatePanel = new TierStatePanel();
    private final GraphPanel graphPanel = new GraphPanel();
    private final SimulationController controller;   // kept as a field so it is clearly owned by the window

    public MainFrame() {
        super("Cloud-Cluster CPU Scheduling Simulator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Results", resultsPanel);
        tabs.addTab("Time steps (tier state)", tierStatePanel);
        tabs.addTab("Q / CS graph", graphPanel);

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, ganttPanel, tabs);
        right.setResizeWeight(0.0);       // extra height goes to the tables, not the chart
        right.setDividerLocation(150);

        setLayout(new BorderLayout(8, 8));
        add(inputPanel, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);

        controller = new SimulationController(inputPanel, ganttPanel, resultsPanel, tierStatePanel,
                graphPanel, () -> tabs.setSelectedComponent(graphPanel), this);

        setSize(1150, 700);
        setMinimumSize(new java.awt.Dimension(800, 500));
        setLocationRelativeTo(null);
    }
}