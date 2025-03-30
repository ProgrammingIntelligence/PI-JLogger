package functions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe Logger implementation with multiple log levels
 * and configurable output destinations.
 */
public final class Logger {
    // Log level enumeration
    public enum Level {
        DEBUG, INFO, WARNING, ERROR, FATAL
    }

    // Singleton instance
    private static volatile Logger instance;
    private static final ReentrantLock instanceLock = new ReentrantLock();

    // Configuration
    private Level minLevel = Level.INFO;
    private final Set<LogOutput> outputs = new HashSet<>();
    private final DateTimeFormatter timestampFormatter = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final ReentrantLock logLock = new ReentrantLock();

    // Private constructor for singleton
    private Logger() {
        // Default output to console
        addOutput(new ConsoleOutput());
    }

    /**
     * Get the singleton Logger instance
     */
    public static Logger getInstance() {
        if (instance == null) {
            instanceLock.lock();
            try {
                if (instance == null) {
                    instance = new Logger();
                }
            } finally {
                instanceLock.unlock();
            }
        }
        return instance;
    }

    /**
     * Set the minimum log level
     */
    public void setMinLevel(Level level) {
        this.minLevel = level;
    }

    /**
     * Add a new output destination
     */
    public void addOutput(LogOutput output) {
        outputs.add(output);
    }

    /**
     * Remove an output destination
     */
    public void removeOutput(LogOutput output) {
        outputs.remove(output);
    }

    /**
     * Core logging method
     */
    public void log(Level level, String message, Throwable throwable) {
        if (level.ordinal() < minLevel.ordinal()) {
            return;
        }

        String timestamp = LocalDateTime.now().format(timestampFormatter);
        String formattedMessage = String.format("[%s] [%s] %s",
            timestamp,
            level.toString(),
            message);

        logLock.lock();
        try {
            for (LogOutput output : outputs) {
                try {
                    output.write(formattedMessage);
                    if (throwable != null) {
                        output.write(stackTraceToString(throwable));
                    }
                } catch (Exception e) {
                    System.err.println("Failed to write to log output: " + e.getMessage());
                }
            }
        } finally {
            logLock.unlock();
        }
    }

    // Convenience methods
    public void debug(String message) { log(Level.DEBUG, message, null); }
    public void info(String message) { log(Level.INFO, message, null); }
    public void warn(String message) { log(Level.WARNING, message, null); }
    public void error(String message) { log(Level.ERROR, message, null); }
    public void fatal(String message) { log(Level.FATAL, message, null); }
    
    public void debug(String message, Throwable t) { log(Level.DEBUG, message, t); }
    public void info(String message, Throwable t) { log(Level.INFO, message, t); }
    public void warn(String message, Throwable t) { log(Level.WARNING, message, t); }
    public void error(String message, Throwable t) { log(Level.ERROR, message, t); }
    public void fatal(String message, Throwable t) { log(Level.FATAL, message, t); }

    /**
     * Convert stack trace to string
     */
    private String stackTraceToString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\t").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Interface for log outputs
     */
    public interface LogOutput {
        void write(String message) throws IOException;
    }

    /**
     * Console output implementation
     */
    public static class ConsoleOutput implements LogOutput {
        @Override
        public void write(String message) {
            System.out.println(message);
        }
    }

    /**
     * File output implementation
     */
    public static class FileOutput implements LogOutput {
        private final Path logFile;

        public FileOutput(String filePath) throws IOException {
            this.logFile = Paths.get(filePath);
            if (!Files.exists(logFile.getParent())) {
                Files.createDirectories(logFile.getParent());
            }
        }

        @Override
        public void write(String message) throws IOException {
            Files.write(logFile, (message + System.lineSeparator()).getBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }
}
