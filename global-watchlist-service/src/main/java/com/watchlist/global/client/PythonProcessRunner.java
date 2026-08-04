package com.watchlist.global.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runs a Python script as a short-lived child process and captures its stdout.
 *
 * <p>WHY a subprocess instead of a persistent Flask server: the previous design ran
 * yfinance_wrapper.py as an always-on background service that Java called over HTTP.
 * That meant Python — and everything pandas/numpy/yfinance load into memory — sat
 * resident 24/7, contributing to repeated EC2 OOM kills, even though the actual work
 * (one batch fetch) takes a few seconds. This runs Python only for the duration of
 * one fetch; the OS reclaims all of its memory the moment the process exits.
 *
 * <p>WHY stdout/stderr are drained on separate threads concurrently with waiting for
 * exit: if the child writes enough output to fill the OS pipe buffer before anyone
 * reads it, the child blocks on its next write. If Java calls {@link Process#waitFor}
 * before ever reading the stream, both sides can deadlock forever. Reading
 * concurrently with the wait avoids this entirely.
 */
@Component
public class PythonProcessRunner {

    private static final Logger log = LogManager.getLogger(PythonProcessRunner.class);

    private final String pythonExecutable;
    private final long timeoutSeconds;

    public PythonProcessRunner(
            @Value("${python.executable:python}") String pythonExecutable,
            @Value("${python.process.timeout-seconds:60}") long timeoutSeconds) {
        this.pythonExecutable = pythonExecutable;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Runs {@code <pythonExecutable> <scriptPath>} to completion and returns its stdout.
     *
     * <p>Diagnostic output on stderr (e.g. "skipping SYMBOL — reason") is logged at WARN
     * but never treated as fatal on its own — only a non-zero exit code, a timeout, or a
     * failure to start the process causes this to return {@code null}.
     *
     * @param scriptPath absolute or working-directory-relative path to the script
     * @return the script's full stdout content, or {@code null} if it failed to start,
     *         timed out, or exited with a non-zero status
     */
    public String run(String scriptPath) {
        ProcessBuilder pb = new ProcessBuilder(pythonExecutable, scriptPath);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            log.error("Failed to start Python process [{} {}]: {}", pythonExecutable, scriptPath, e.getMessage());
            return null;
        }

        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            log.error("Interrupted while waiting for Python process [{}]", scriptPath);
            return null;
        }

        if (!finished) {
            process.destroyForcibly();
            log.error("Python process [{}] timed out after {}s — killed", scriptPath, timeoutSeconds);
            return null;
        }

        String out = stdout.join();
        String err = stderr.join();
        if (!err.isBlank()) {
            log.warn("Python process [{}] stderr:\n{}", scriptPath, err);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("Python process [{}] exited with code {}", scriptPath, exitCode);
            return null;
        }

        return out;
    }

    private String readStream(InputStream is) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            log.warn("Error reading process stream: {}", e.getMessage());
        }
        return sb.toString();
    }
}