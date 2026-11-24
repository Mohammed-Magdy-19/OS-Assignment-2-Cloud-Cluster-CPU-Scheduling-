package scheduler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.GanttEntry;
import model.Process;
import model.SimulationResult;
import model.TimeStepSnapshot;

public class ACScheduler implements Scheduler {
    private static final int AGING_INTERVAL = 4;
    @Override
    public SimulationResult run(List<Process> processes, int contextSwitch, int quantum) {
        if (processes == null || processes.isEmpty()) {
            throw new IllegalArgumentException("Processes list must not be null or empty");
        }
        if (contextSwitch < 0) {
            throw new IllegalArgumentException("Context switch time must be >= 0");
        }
        if (quantum <= 0) {
            throw new IllegalArgumentException("Quantum must be > 0");
        }

        List<Process> simProcesses = new ArrayList<>();
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

        List<Process> tier1 = new ArrayList<>();
        List<Process> tier2 = new ArrayList<>();

        Map<Process, Integer> demotionTime = new HashMap<>();
        boolean[] added = new boolean[simProcesses.size()];
        List<TimeStepSnapshot> snapshots = new ArrayList<>();
        List<GanttEntry> rawGantt = new ArrayList<>();
        int now = 0;
        int finishedCount = 0;
        Process running = null;
        Process lastProcess = null;
        int runningTier = 0;
        int quantumUsed = 0;
        while (finishedCount < simProcesses.size()) {

            addArrivals(simProcesses, tier1, added, now);
            if (running == null) {
                if (!tier1.isEmpty()) {
                    running = tier1.get(0);
                    runningTier = 1;
                } else if (!tier2.isEmpty()) {
                    running = getHighestPriority(tier2, demotionTime, now, inputOrder);
                    runningTier = 2;
                } else {
                    snapshots.add(new TimeStepSnapshot(
                            now,
                            GanttEntry.IDLE_LABEL,
                            Collections.emptyList(),
                            Collections.emptyList()
                    ));
                    rawGantt.add(GanttEntry.idle(now, now + 1));
                    lastProcess = null;
                    now++;
                    continue;
                }
                if (lastProcess != null && lastProcess != running && contextSwitch > 0) {

                    for (int i = 0; i < contextSwitch; i++) {
                        addArrivals(simProcesses, tier1, added, now);
                        snapshots.add(new TimeStepSnapshot(
                                now,
                                GanttEntry.CONTEXT_SWITCH_LABEL,
                                formatQueue(tier1, null, demotionTime, now),
                                formatQueue(tier2, null, demotionTime, now)
                        ));

                        rawGantt.add(
                                GanttEntry.contextSwitch(now, now + 1)
                        );
                        now++;
                    }
                }
                if (runningTier == 1) {
                    tier1.remove(running);
                } else {
                    tier2.remove(running);
                }

                quantumUsed = 0;
                running.recordFirstRun(now);
            }
            snapshots.add(new TimeStepSnapshot(now,running.getName(),formatQueue(tier1, running, demotionTime, now),formatQueue(tier2, running, demotionTime, now)));

            rawGantt.add(GanttEntry.process(running.getName(), now, now + 1));
            running.run(1);
            now++;
            if (runningTier == 1) {
                quantumUsed++;
                if (running.getRemaining() == 0) {
                    running.markFinished(now);
                    finishedCount++;
                    lastProcess = running;
                    running = null;
                    quantumUsed = 0;

                }
                else if (quantumUsed == quantum) {
                    tier2.add(running);
                    demotionTime.put(running, now);
                    lastProcess = running;
                    running = null;
                    quantumUsed = 0;
                }
            }

            else if (runningTier == 2) {
                if (running.getRemaining() == 0) {
                    running.markFinished(now);
                    finishedCount++;
                    lastProcess = running;
                    running = null;
                }

            }
        }
        List<GanttEntry> mergedGantt = mergeConsecutiveGantt(rawGantt);
        return new SimulationResult(snapshots,mergedGantt,simProcesses);
    }

    private static void addArrivals(List<Process> processes,List<Process> tier1,boolean[] added,int now) {

        for (int i = 0; i < processes.size(); i++) {
            Process p = processes.get(i);
            if (!added[i] && p.getArrival() <= now) {
                tier1.add(p);
                added[i] = true;
            }
        }
    }


    private static int effectivePriority(Process p,int now,Map<Process, Integer> demotionTime) {
        int waitingTime = now - demotionTime.get(p);
        int aging = waitingTime / AGING_INTERVAL;
        int priority = p.getPriority() - aging;
        return Math.max(0, priority);
    }


    private static Process getHighestPriority(List<Process> tier2,Map<Process, Integer> demotionTime,int now,Map<Process, Integer> inputOrder) {

        Process chosen = tier2.get(0);
        for (int i = 1; i < tier2.size(); i++) {
            Process current = tier2.get(i);
            int currentPriority = effectivePriority(current, now, demotionTime);
            int chosenPriority = effectivePriority(chosen, now, demotionTime);
            if (currentPriority < chosenPriority) {
                chosen = current;
            } else if (currentPriority == chosenPriority) {
                if (current.getArrival() < chosen.getArrival()) {
                    chosen = current;

                } else if (current.getArrival() == chosen.getArrival() && inputOrder.get(current) < inputOrder.get(chosen)) {
                    chosen = current;
                }
            }
        }

        return chosen;
    }


    private static List<String> formatQueue(List<Process> queue,Process running,Map<Process, Integer> demotionTime,int now) {

        List<String> formatted = new ArrayList<>();
        for (Process p : queue) {
            int priority = p.getPriority();
            if (demotionTime.containsKey(p)) {
                priority = effectivePriority(p, now, demotionTime);
            }

            formatted.add(p.getName()+ " (remaining: " + p.getRemaining() + ", Priority: " + priority + ")");
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
            if (current.getType() == next.getType() && current.getLabel().equals(next.getLabel()) && current.getEnd() == next.getStart()) {

                current = createEntry(current.getType(),current.getLabel(),current.getStart(),next.getEnd());

            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static GanttEntry createEntry(GanttEntry.Type type,String label,int start,int end) {

        switch (type) {
            case PROCESS:
                return GanttEntry.process(label, start, end);
            case CONTEXT_SWITCH:
                return GanttEntry.contextSwitch(start, end);
            case IDLE:
                return GanttEntry.idle(start, end);
            default:
                throw new IllegalStateException("Unknown GanttEntry type");
        }
    }
}

