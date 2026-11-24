package model;

/**
 * One process in the simulation.
 *
 * Input values (name, arrival, burst, priority) are final and validated.
 * Runtime state (remaining, start, finish) can only change through the
 * methods below, and every method rejects illegal use instead of failing silently.
 */
public final class Process {

    /** Marks a time value that has not been recorded yet. */
    public static final int NOT_SET = -1;

    private final String name;
    private final int arrival;
    private final int burst;
    private final int priority;

    private int remaining;
    private int startTime = NOT_SET;
    private int finishTime = NOT_SET;

    public Process(String name, int arrival, int burst, int priority) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Process name must not be empty");
        }
        if (arrival < 0) {
            throw new IllegalArgumentException(name + ": arrival time must be >= 0, got " + arrival);
        }
        if (burst <= 0) {
            throw new IllegalArgumentException(name + ": burst time must be > 0, got " + burst);
        }
        if (priority < 0) {
            throw new IllegalArgumentException(name + ": priority must be >= 0, got " + priority);
        }
        this.name = name.trim();
        this.arrival = arrival;
        this.burst = burst;
        this.priority = priority;
        this.remaining = burst;
    }

    /** Private copy constructor: keeps the runtime state too. */
    private Process(Process other) {
        this.name = other.name;
        this.arrival = other.arrival;
        this.burst = other.burst;
        this.priority = other.priority;
        this.remaining = other.remaining;
        this.startTime = other.startTime;
        this.finishTime = other.finishTime;
    }

    // ---------------------------------------------------------------- input data

    public String getName()     { return name; }
    public int getArrival()     { return arrival; }
    public int getBurst()       { return burst; }
    public int getPriority()    { return priority; }

    // ------------------------------------------------------------- runtime state

    public int getRemaining()   { return remaining; }
    public int getStartTime()   { return startTime; }
    public int getFinishTime()  { return finishTime; }

    public boolean hasStarted() { return startTime != NOT_SET; }
    public boolean isFinished() { return finishTime != NOT_SET; }

    /** Consume CPU time. Never allows remaining to go below zero. */
    public void run(int units) {
        if (isFinished()) {
            throw new IllegalStateException(name + " is already finished");
        }
        if (units <= 0 || units > remaining) {
            throw new IllegalArgumentException(
                    name + ": cannot run " + units + " units, remaining = " + remaining);
        }
        remaining -= units;
    }

    /** Record the first time this process gets the CPU. Later calls are ignored on purpose. */
    public void recordFirstRun(int time) {
        if (time < arrival) {
            throw new IllegalArgumentException(
                    name + ": cannot start at " + time + " before arrival " + arrival);
        }
        if (startTime == NOT_SET) {
            startTime = time;
        }
    }

    /** Record completion. Only valid when all burst time has been consumed. */
    public void markFinished(int time) {
        if (isFinished()) {
            throw new IllegalStateException(name + " is already finished");
        }
        if (remaining != 0) {
            throw new IllegalStateException(name + " still has " + remaining + " units remaining");
        }
        if (!hasStarted()) {
            throw new IllegalStateException(name + " finished without ever starting");
        }
        if (time <= startTime) {
            throw new IllegalArgumentException(
                    name + ": finish time " + time + " must be after start time " + startTime);
        }
        finishTime = time;
    }

    /** Return to the initial state so the same input can be simulated again. */
    public void reset() {
        remaining = burst;
        startTime = NOT_SET;
        finishTime = NOT_SET;
    }

    /** Independent copy (including runtime state). */
    public Process copy() {
        return new Process(this);
    }

    // ------------------------------------------------------------------- metrics

    public int getTurnaroundTime() {
        requireFinished();
        return finishTime - arrival;
    }

    public int getWaitingTime() {
        requireFinished();
        return getTurnaroundTime() - burst;
    }

    public int getResponseTime() {
        if (!hasStarted()) {
            throw new IllegalStateException(name + " has not started, response time is undefined");
        }
        return startTime - arrival;
    }

    private void requireFinished() {
        if (!isFinished()) {
            throw new IllegalStateException(name + " is not finished, metric is undefined");
        }
    }

    @Override
    public String toString() {
        return name + " (remaining: " + remaining + ", Priority: " + priority + ")";
    }
}