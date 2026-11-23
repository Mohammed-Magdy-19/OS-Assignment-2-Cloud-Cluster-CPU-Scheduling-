package scheduler;

import java.util.List;
import model.Process;
import model.SimulationResult;

public interface Scheduler {

    SimulationResult run(List<Process> processes, int contextSwitch, int quantum);
}