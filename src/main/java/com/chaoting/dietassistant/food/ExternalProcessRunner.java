package com.chaoting.dietassistant.food;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
class ExternalProcessRunner {

    Result run(List<String> arguments, Duration timeout) throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        Process process = new ProcessBuilder(arguments).start();
        process.getOutputStream().close();
        var stdout = new java.io.ByteArrayOutputStream();
        var stderr = new java.io.ByteArrayOutputStream();
        Thread stdoutReader = drain(process.getInputStream(), stdout);
        Thread stderrReader = drain(process.getErrorStream(), stderr);
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            destroyDescendants(process, false);
            process.destroy();
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                destroyDescendants(process, true);
                process.destroyForcibly();
            }
            stdoutReader.join();
            stderrReader.join();
            throw new ProcessTimeoutException("External process timed out.", elapsed(startedAt),
                    stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8), process.isAlive());
        }
        stdoutReader.join();
        stderrReader.join();
        return new Result(process.exitValue(), stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8), elapsed(startedAt));
    }

    private Thread drain(java.io.InputStream input, java.io.OutputStream output) {
        return Thread.startVirtualThread(() -> {
            try (input) { input.transferTo(output); } catch (IOException ignored) { }
        });
    }

    private void destroyDescendants(Process process, boolean forcibly) {
        try {
            process.descendants().forEach(handle -> {
                if (forcibly) handle.destroyForcibly();
                else handle.destroy();
            });
        } catch (RuntimeException ignored) {
            // Some restricted macOS environments deny ProcessHandle descendant discovery.
            // The exact launched process is still terminated below.
        }
    }

    private Duration elapsed(long startedAt) { return Duration.ofNanos(System.nanoTime() - startedAt); }

    record Result(int exitCode, String stdout, String stderr, Duration elapsed) {
        String output() { return stdout + stderr; }
    }

    static class ProcessTimeoutException extends IOException {
        private final Duration elapsed;
        private final String stdout;
        private final String stderr;
        private final boolean processAlive;

        ProcessTimeoutException(String message, Duration elapsed, String stdout, String stderr, boolean processAlive) {
            super(message);
            this.elapsed = elapsed;
            this.stdout = stdout;
            this.stderr = stderr;
            this.processAlive = processAlive;
        }

        Duration elapsed() { return elapsed; }
        String stdout() { return stdout; }
        String stderr() { return stderr; }
        boolean processAlive() { return processAlive; }
    }
}
