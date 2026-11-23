package scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.GanttEntry;
import model.Process;
import model.SimulationResult;
import model.TimeStepSnapshot;

public class PreemptiveSJF implements Scheduler {

    @Override
    public SimulationResult run(List<Process> processes, int contextSwitch, int quantum) {
        if (processes == null || processes.isEmpty()) {
            throw new IllegalArgumentException("Processes list must not be null or empty");
        }
        if (contextSwitch < 0) {
            throw new IllegalArgumentException("Context switch time must be >= 0, got " + contextSwitch);
        }

        List<Process> simProcesses = new ArrayList<>(processes.size());
        Map<Process, Integer> inputOrder = new HashMap<>();
        for (int i = 0; i < processes.size(); i++) {
            Process original = processes.get(i);
            if (original == null) {
                throw new IllegalArgumentException("Processes list must not contain null");
            }
            Process copy = original.copy();
            copy.reset();
            simProcesses.add(copy);
            inputOrder.put(copy, i);
        }

        Comparator<Process> sjfComparator = (a, b) -> {
            int cmp = Integer.compare(a.getRemaining(), b.getRemaining());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(a.getArrival(), b.getArrival());
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(inputOrder.get(a), inputOrder.get(b));
        };

        List<TimeStepSnapshot> snapshots = new ArrayList<>();
        List<GanttEntry> rawGantt = new ArrayList<>();

        int totalProcesses = simProcesses.size();
        int finishedCount = 0;
        int now = 0;

        Process lastProcess = null;   
        int csRemaining = 0;          
        boolean justFinishedCS = false;

        while (finishedCount < totalProcesses) {
            List<Process> ready = new ArrayList<>();
            for (Process p : simProcesses) {
                if (p.getArrival() <= now && !p.isFinished()) {
                    ready.add(p);
                }
            }
            ready.sort(sjfComparator);

            if (csRemaining > 0) {
                snapshots.add(new TimeStepSnapshot(
                        now,
                        GanttEntry.CONTEXT_SWITCH_LABEL,
                        formatQueue(ready),
                        Collections.emptyList()));
                rawGantt.add(GanttEntry.contextSwitch(now, now + 1));

                csRemaining--;
                if (csRemaining == 0) {
                    justFinishedCS = true;
                }
                now++;
                continue;
            }

            if (ready.isEmpty()) {
                snapshots.add(new TimeStepSnapshot(
                        now,
                        GanttEntry.IDLE_LABEL,
                        Collections.emptyList(),
                        Collections.emptyList()));
                rawGantt.add(GanttEntry.idle(now, now + 1));

                lastProcess = null;
                justFinishedCS = false;
                now++;
                continue;
            }

            Process chosen = ready.get(0);
            if (lastProcess != null && !lastProcess.isFinished() && ready.contains(lastProcess)) {
                if (lastProcess.getRemaining() <= chosen.getRemaining()) {
                    chosen = lastProcess;
                }
            }

            if (!justFinishedCS && lastProcess != null && lastProcess != chosen && contextSwitch > 0) {
                csRemaining = contextSwitch;
                continue;
            }

            justFinishedCS = false;

            List<String> waiting = new ArrayList<>();
            for (Process p : ready) {
                if (p != chosen) {
                    waiting.add(formatProcess(p));
                }
            }

            snapshots.add(new TimeStepSnapshot(
                    now,
                    chosen.getName(),
                    waiting,
                    Collections.emptyList()));
            rawGantt.add(GanttEntry.process(chosen.getName(), now, now + 1));

            chosen.recordFirstRun(now);
            chosen.run(1);
            now++;

            if (chosen.getRemaining() == 0) {
                chosen.markFinished(now);
                finishedCount++;
            }

            lastProcess = chosen;
        }

        List<GanttEntry> mergedGantt = mergeConsecutiveGantt(rawGantt);
        return new SimulationResult(snapshots, mergedGantt, simProcesses);
    }

    private static String formatProcess(Process p) {
        return p.getName() + " (remaining: " + p.getRemaining() + ")";
    }

    private static List<String> formatQueue(List<Process> queue) {
        List<String> formatted = new ArrayList<>(queue.size());
        for (Process p : queue) {
            formatted.add(formatProcess(p));
        }
        return formatted;
    }

    private static List<GanttEntry> mergeConsecutiveGantt(List<GanttEntry> raw) {
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<GanttEntry> merged = new ArrayList<>();
        GanttEntry current = raw.get(0);

        for (int i = 1; i < raw.size(); i++) {
            GanttEntry next = raw.get(i);
            if (current.getType() == next.getType()
                    && current.getLabel().equals(next.getLabel())
                    && current.getEnd() == next.getStart()) {
                current = createEntry(current.getType(), current.getLabel(), current.getStart(), next.getEnd());
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static GanttEntry createEntry(GanttEntry.Type type, String label, int start, int end) {
        switch (type) {
            case PROCESS:
                return GanttEntry.process(label, start, end);
            case CONTEXT_SWITCH:
                return GanttEntry.contextSwitch(start, end);
            case IDLE:
                return GanttEntry.idle(start, end);
            default:
                throw new IllegalStateException("Unknown GanttEntry type: " + type);
        }
    }
}