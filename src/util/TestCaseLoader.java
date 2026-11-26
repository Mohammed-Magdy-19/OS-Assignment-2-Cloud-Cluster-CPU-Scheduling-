package util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import model.Process;

/**
 * Loads the assignment's test cases from disk or falls back to built-in copies.
 *
 * <p>File lookup order for {@link #load(int)}:
 * <ol>
 *   <li>{@code testcases/test<N>.txt} relative to the current working directory</li>
 *   <li>{@code ../testcases/test<N>.txt} (for example when launched from {@code src/} or an IDE)</li>
 * </ol>
 * If neither file exists, {@link #load(int)} prints a warning to {@code System.err} and
 * returns {@link #hardcoded(int)}. If a file exists but is malformed, its
 * {@link IllegalArgumentException} is propagated without falling back.
 */
public final class TestCaseLoader {

    /** Number of built-in assignment test cases. */
    public static final int TEST_COUNT = 2;

    /** Upper bound for every numeric field, matching InputPanel.MAX_VALUE without importing the view. */
    private static final int MAX_VALUE = 1000;

    /** Utility class: prevent instantiation. */
    private TestCaseLoader() {
    }

    /**
     * Immutable data holder for one test case (name, quantum, context switch, and process list).
     */
    public static final class TestCase {

        private final String name;
        private final int quantum;
        private final int contextSwitch;
        private final List<Process> processes;

        /**
         * Creates a new immutable test case, storing fresh copies of the given processes.
         */
        public TestCase(String name, int quantum, int contextSwitch, List<Process> processes) {
            this.name = Objects.requireNonNull(name, "name");
            if (quantum < 1 || quantum > MAX_VALUE) {
                throw new IllegalArgumentException("Quantum must be between 1 and " + MAX_VALUE + ", got " + quantum);
            }
            if (contextSwitch < 0 || contextSwitch > MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Context switch must be between 0 and " + MAX_VALUE + ", got " + contextSwitch);
            }
            Objects.requireNonNull(processes, "processes");
            if (processes.isEmpty()) {
                throw new IllegalArgumentException("Test case must contain at least one process");
            }
            this.quantum = quantum;
            this.contextSwitch = contextSwitch;
            this.processes = copyAndReset(processes);
        }

        public String getName() {
            return name;
        }

        public int getQuantum() {
            return quantum;
        }

        public int getContextSwitch() {
            return contextSwitch;
        }

        /**
         * Returns a new list of fresh, independent {@link Process} objects (with runtime state reset)
         * in the original input order. Callers can mutate or run them without affecting this test case.
         */
        public List<Process> getProcesses() {
            return copyAndReset(processes);
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "%s (Q=%d, CS=%d, %d processes)",
                    name, quantum, contextSwitch, processes.size());
        }

        private static List<Process> copyAndReset(List<Process> source) {
            List<Process> copies = new ArrayList<>(source.size());
            for (Process p : source) {
                Objects.requireNonNull(p, "process");
                Process copy = p.copy();
                copy.reset();
                copies.add(copy);
            }
            return copies;
        }
    }

    /**
     * Loads test case {@code number} (1..{@link #TEST_COUNT}) from disk if present,
     * or falls back to {@link #hardcoded(int)} if the file cannot be found.
     *
     * <p>Lookup order: {@code testcases/test<N>.txt}, then {@code ../testcases/test<N>.txt}.
     * If a file is found but malformed, {@link IllegalArgumentException} is thrown directly.
     */
    public static TestCase load(int number) {
        validateTestNumber(number);

        String fileName = "test" + number + ".txt";
        Path primary = Paths.get("testcases", fileName);
        Path fallback = Paths.get("..", "testcases", fileName);

        Path found = null;
        if (Files.isRegularFile(primary)) {
            found = primary;
        } else if (Files.isRegularFile(fallback)) {
            found = fallback;
        }

        if (found == null) {
            System.err.println("Warning: " + primary + " (and " + fallback
                    + ") not found; using hardcoded Test Case " + number + ".");
            return hardcoded(number);
        }

        try {
            return loadFromFile(found);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read test case file " + found + ": " + e.getMessage(), e);
        }
    }

    /**
     * Strictly parses a test-case file in UTF-8 format.
     *
     * @throws IOException              if reading the file fails
     * @throws IllegalArgumentException if any setting or process line violates the format contract
     */
    public static TestCase loadFromFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        String fileName = file.getFileName() != null ? file.getFileName().toString() : file.toString();

        // Files.readAllLines(Path) decodes using UTF-8 in Java 8.
        List<String> lines = Files.readAllLines(file);

        String name = null;
        Integer quantum = null;
        Integer contextSwitch = null;
        List<Process> processes = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String line = lines.get(i);

            // Tolerate a UTF-8 BOM at the very start of the first line.
            if (i == 0 && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }

            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int eqIndex = line.indexOf('=');
            if (eqIndex != -1) {
                String key = line.substring(0, eqIndex).trim();
                String value = line.substring(eqIndex + 1).trim();
                String upperKey = key.toUpperCase(Locale.US);

                if ("NAME".equals(upperKey)) {
                    if (name != null) {
                        throw lineError(fileName, lineNumber, "duplicate setting 'Name'");
                    }
                    if (value.isEmpty()) {
                        throw lineError(fileName, lineNumber, "setting 'Name' must not be empty");
                    }
                    name = value;
                } else if ("Q".equals(upperKey)) {
                    if (quantum != null) {
                        throw lineError(fileName, lineNumber, "duplicate setting 'Q'");
                    }
                    int parsedQ = parseBoundedInt(fileName, lineNumber, "Q", value, 1, MAX_VALUE);
                    quantum = parsedQ;
                } else if ("CS".equals(upperKey)) {
                    if (contextSwitch != null) {
                        throw lineError(fileName, lineNumber, "duplicate setting 'CS'");
                    }
                    int parsedCs = parseBoundedInt(fileName, lineNumber, "CS", value, 0, MAX_VALUE);
                    contextSwitch = parsedCs;
                } else {
                    throw lineError(fileName, lineNumber,
                            "unknown setting '" + key + "' (expected Name, Q, or CS)");
                }
            } else {
                String[] parts = line.split("\\s+");
                if (parts.length != 4) {
                    throw lineError(fileName, lineNumber,
                            "expected 4 fields (name arrival burst priority), got " + parts.length);
                }

                String procName = parts[0];
                int arrival = parseBoundedInt(fileName, lineNumber, "arrival", parts[1], 0, MAX_VALUE);
                int burst = parseBoundedInt(fileName, lineNumber, "burst", parts[2], 1, MAX_VALUE);
                int priority = parseBoundedInt(fileName, lineNumber, "priority", parts[3], 0, MAX_VALUE);

                Process process;
                try {
                    process = new Process(procName, arrival, burst, priority);
                } catch (IllegalArgumentException e) {
                    throw lineError(fileName, lineNumber, e.getMessage());
                }

                if (!seenNames.add(process.getName())) {
                    throw lineError(fileName, lineNumber,
                            "duplicate process name '" + process.getName() + "' (names must be unique)");
                }
                processes.add(process);
            }
        }

        if (quantum == null) {
            throw new IllegalArgumentException(fileName + ": missing required setting Q");
        }
        if (contextSwitch == null) {
            throw new IllegalArgumentException(fileName + ": missing required setting CS");
        }
        if (processes.isEmpty()) {
            throw new IllegalArgumentException(fileName + ": file must contain at least one process");
        }
        if (name == null) {
            name = stripExtension(fileName);
        }

        return new TestCase(name, quantum, contextSwitch, processes);
    }

    /**
     * Returns the built-in copy of assignment test case {@code number} (1..{@link #TEST_COUNT}),
     * matching the assignment PDF and {@code testcases/test<N>.txt}.
     */
    public static TestCase hardcoded(int number) {
        validateTestNumber(number);

        List<Process> processes = new ArrayList<>();
        if (number == 1) {
            processes.add(new Process("P1", 0, 4, 2));
            processes.add(new Process("P2", 1, 7, 3));
            processes.add(new Process("P3", 3, 2, 1));
            return new TestCase("Test Case 1", 3, 0, processes);
        } else {
            processes.add(new Process("P1", 0, 5, 4));
            processes.add(new Process("P2", 1, 6, 2));
            processes.add(new Process("P3", 4, 4, 3));
            return new TestCase("Test Case 2", 3, 1, processes);
        }
    }

    // --------------------------------------------------------------------- helpers

    private static void validateTestNumber(int number) {
        if (number < 1 || number > TEST_COUNT) {
            throw new IllegalArgumentException(
                    "Test case number must be between 1 and " + TEST_COUNT + ", got " + number);
        }
    }

    private static int parseBoundedInt(String fileName, int lineNumber, String fieldName,
                                       String text, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw lineError(fileName, lineNumber,
                    fieldName + " must be an integer, got '" + text + "'");
        }
        if (value < min || value > max) {
            throw lineError(fileName, lineNumber,
                    fieldName + " must be between " + min + " and " + max + ", got " + value);
        }
        return value;
    }

    private static IllegalArgumentException lineError(String fileName, int lineNumber, String detail) {
        return new IllegalArgumentException(fileName + ", line " + lineNumber + ": " + detail);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
