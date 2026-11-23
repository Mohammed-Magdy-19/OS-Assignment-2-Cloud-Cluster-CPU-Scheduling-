package model;

import java.util.Objects;

/** One continuous block on the Gantt chart: a process running, a context switch, or idle CPU. */
public final class GanttEntry {

    public enum Type { PROCESS, CONTEXT_SWITCH, IDLE }

    public static final String CONTEXT_SWITCH_LABEL = "CS";
    public static final String IDLE_LABEL = "idle";

    private final Type type;
    private final String label;
    private final int start;
    private final int end;

    private GanttEntry(Type type, String label, int start, int end) {
        if (start < 0) {
            throw new IllegalArgumentException("Gantt start must be >= 0, got " + start);
        }
        if (end <= start) {
            throw new IllegalArgumentException(
                    "Gantt end (" + end + ") must be greater than start (" + start + ")");
        }
        this.type = type;
        this.label = label;
        this.start = start;
        this.end = end;
    }

    public static GanttEntry process(String processName, int start, int end) {
        Objects.requireNonNull(processName, "processName");
        if (processName.trim().isEmpty()) {
            throw new IllegalArgumentException("Process name must not be empty");
        }
        return new GanttEntry(Type.PROCESS, processName.trim(), start, end);
    }

    public static GanttEntry contextSwitch(int start, int end) {
        return new GanttEntry(Type.CONTEXT_SWITCH, CONTEXT_SWITCH_LABEL, start, end);
    }

    public static GanttEntry idle(int start, int end) {
        return new GanttEntry(Type.IDLE, IDLE_LABEL, start, end);
    }

    public Type getType()    { return type; }
    public String getLabel() { return label; }
    public int getStart()    { return start; }
    public int getEnd()      { return end; }
    public int getDuration() { return end - start; }

    @Override
    public String toString() {
        return label + " [" + start + " - " + end + "]";
    }
}