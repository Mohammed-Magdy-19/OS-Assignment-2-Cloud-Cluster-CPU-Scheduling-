package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * State of the system at one time step: who is on the CPU and what is in each tier.
 * Immutable: the queue lists are copied on the way in and cannot be modified afterwards.
 */
public final class TimeStepSnapshot {

    private final int time;
    private final String running;
    private final List<String> tier1;
    private final List<String> tier2;

    /**
     * @param running process name, {@link GanttEntry#CONTEXT_SWITCH_LABEL} or {@link GanttEntry#IDLE_LABEL}
     * @param tier1   formatted Tier 1 queue (for SJF: the ready queue)
     * @param tier2   formatted Tier 2 queue (for SJF: pass an empty list)
     */
    public TimeStepSnapshot(int time, String running, List<String> tier1, List<String> tier2) {
        if (time < 0) {
            throw new IllegalArgumentException("Time must be >= 0, got " + time);
        }
        if (running == null || running.trim().isEmpty()) {
            throw new IllegalArgumentException("Running label must not be empty");
        }
        this.time = time;
        this.running = running.trim();
        this.tier1 = safeCopy(tier1, "tier1");
        this.tier2 = safeCopy(tier2, "tier2");
    }

    private static List<String> safeCopy(List<String> source, String field) {
        if (source == null) {
            throw new IllegalArgumentException(field + " must not be null (use an empty list)");
        }
        List<String> copy = new ArrayList<>(source.size());
        for (String item : source) {
            if (item == null) {
                throw new IllegalArgumentException(field + " must not contain null");
            }
            copy.add(item);
        }
        return Collections.unmodifiableList(copy);
    }

    public int getTime()          { return time; }
    public String getRunning()    { return running; }
    public List<String> getTier1() { return tier1; }
    public List<String> getTier2() { return tier2; }

    private static String format(List<String> queue) {
        return queue.isEmpty() ? "[Empty]" : "[" + String.join(", ", queue) + "]";
    }

    @Override
    public String toString() {
        return "t=" + time + " | " + running
                + " | Tier 1: " + format(tier1)
                + " | Tier 2: " + format(tier2);
    }
}