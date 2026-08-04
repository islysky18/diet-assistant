package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalProcessRunnerTest {

    private final ExternalProcessRunner runner = new ExternalProcessRunner();

    @Test
    void drainsStdoutAndStderrAndReturnsNonZeroExit() throws Exception {
        var result = runner.run(List.of("/bin/sh", "-c", "printf out; printf failed >&2; exit 7"), Duration.ofSeconds(2));
        assertThat(result.exitCode()).isEqualTo(7);
        assertThat(result.stdout()).contains("out");
        assertThat(result.stderr()).contains("failed");
        assertThat(result.output()).contains("failed");
    }

    @Test
    void closesChildStdinImmediately() throws Exception {
        var result = runner.run(List.of("/bin/sh", "-c", "read value || printf eof"), Duration.ofSeconds(2));

        assertThat(result.stdout()).isEqualTo("eof");
    }

    @Test
    void concurrentlyDrainsLargeStdoutAndStderr() throws Exception {
        var result = runner.run(List.of("/bin/sh", "-c",
                "i=0; while [ $i -lt 5000 ]; do printf 'stdout-line\\n'; printf 'stderr-line\\n' >&2; i=$((i+1)); done"),
                Duration.ofSeconds(5));

        assertThat(result.stdout()).contains("stdout-line");
        assertThat(result.stderr()).contains("stderr-line");
    }

    @Test
    void terminatesTimedOutProcess() {
        assertThatThrownBy(() -> runner.run(List.of("/bin/sh", "-c", "sleep 5"), Duration.ofMillis(50)))
                .isInstanceOfSatisfying(ExternalProcessRunner.ProcessTimeoutException.class,
                        exception -> assertThat(exception.processAlive()).isFalse());
    }
}
