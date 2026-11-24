package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Final output of one simulation run. Immutable.
 * Processes are copied on construction, so a scheduler can safely reset and reuse its own objects.
 */
public final class SimulationResult {

    private final List<TimeStepSnapshot> steps;
    private final List<GanttEntry> gantt;
    private final List<Process> processes;

    private final double avgWaiting;
    private final double avgTurnaround;
    private final double avgResponse;
    private final int totalTime;
    private final double cpuUtilization;

    public SimulationResult(List<TimeStepSnapshot> steps,
                            List<GanttEntry> gantt,
                            List<Process> processes) {
        if (steps == null || gantt == null || processes == null) {
            throw new IllegalArgumentException("steps, gantt and processes must not be null");
        }
        if (processes.isEmpty()) {
            throw new IllegalArgumentException("A result needs at least one process");
        }
        if (gantt.isEmpty()) {
            throw new IllegalArgumentException("A result needs at least one Gantt entry");
        }

        validateSteps(steps);
        validateGantt(gantt);

        List<Process> copies = new ArrayList<>(processes.size());
        double sumWaiting = 0, sumTurnaround = 0, sumResponse = 0;
        for (Process p : processes) {
            if (p == null) {
                throw new IllegalArgumentException("processes must not contain null");
            }
            if (!p.isFinished()) {          // fails loudly instead of producing wrong averages
                throw new IllegalArgumentException(p.getName() + " did not finish");
            }
            copies.add(p.copy());
            sumWaiting    += p.getWaitingTime();
            sumTurnaround += p.getTurnaroundTime();
            sumResponse   += p.getResponseTime();
        }

        int n = copies.size();
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.gantt = Collections.unmodifiableList(new ArrayList<>(gantt));
        this.processes = Collections.unmodifiableList(copies);
        this.avgWaiting = sumWaiting / n;
        this.avgTurnaround = sumTurnaround / n;
        this.avgResponse = sumResponse / n;

        this.totalTime = gantt.get(gantt.size() - 1).getEnd();
        int busy = 0;
        for (GanttEntry g : gantt) {
            if (g.getType() == GanttEntry.Type.PROCESS) {
                busy += g.getDuration();
            }
        }
        this.cpuUtilization = (double) busy / totalTime;
    }

    private static void validateSteps(List<TimeStepSnapshot> steps) {
        int previous = -1;
        for (TimeStepSnapshot s : steps) {
            if (s == null) {
                throw new IllegalArgumentException("steps must not contain null");
            }
            if (s.getTime() <= previous) {
                throw new IllegalArgumentException(
                        "Snapshots must be in strictly increasing time order (t=" + s.getTime() + ")");
            }
            previous = s.getTime();
        }
    }

    private static void validateGantt(List<GanttEntry> gantt) {
        int previousEnd = 0;
        for (GanttEntry g : gantt) {
            if (g == null) {
                throw new IllegalArgumentException("gantt must not contain null");
            }
            if (g.getStart() < previousEnd) {
                throw new IllegalArgumentException(
                        "Gantt entries overlap or are out of order at " + g);
            }
            previousEnd = g.getEnd();
        }
    }

    public List<TimeStepSnapshot> getSteps() { return steps; }
    public List<GanttEntry> getGantt()       { return gantt; }
    public List<Process> getProcesses()      { return processes; }

    public double getAvgWaiting()    { return avgWaiting; }
    public double getAvgTurnaround() { return avgTurnaround; }
    public double getAvgResponse()   { return avgResponse; }

    /** Time at which the last Gantt entry ends. */
    public int getTotalTime()        { return totalTime; }

    /** Fraction of total time spent running processes (context switches and idle count as not useful). */
    public double getCpuUtilization() { return cpuUtilization; }
}