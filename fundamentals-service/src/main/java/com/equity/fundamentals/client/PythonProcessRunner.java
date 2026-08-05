package com.equity.fundamentals.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runs yfinance_wrapper.py as a short-lived child process for one batch of symbols
 * and captures its stdout.
 *
 * <p>WHY a subprocess instead of a persistent Flask server: the previous design ran
 * the wrapper as an always-on background service (yfinance-fundamentals.service)
 * that Java called over HTTP, once per symbol, per data type — 2000+ times for a
 * full sync. Python — and everything pandas/numpy/yfinance load into memory — sat
 * resident 24/7 regardless of whether a request was in flight, and was a repeated
 * contributor to EC2 OOM kills. Java now spawns this script once per chunk of
 * symbols; the OS reclaims all of its memory the moment the process exits.
 *
 * <p>WHY stdout/stderr are drained on separate threads concurrently with waiting for
 * exit: if the child writes enough output to fill the OS pipe buffer before anyone
 * reads it, the child blocks on its next write. If Java calls {@link Process#waitFor}
 * before ever reading the stream, both sides can deadlock forever. Reading
 * concurrently with the wait avoids this entirely — and matters more here than for
 * a small fixed batch, since a chunk's JSON output can run to hundreds of KB.
 */
@Component
public class PythonProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(PythonProcessRunner.class);

    private final String pythonExecutable;
    private final long timeoutSeconds;

    public PythonProcessRunner(
            @Value("${python.executable:python}") String pythonExecutable,
            @Value("${python.process.timeout-seconds:1200}") long timeoutSeconds) {
        this.pythonExecutable = pythonExecutable;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Runs {@code <pythonExecutable> <scriptPath> --mode <mode> --symbols-file <tmp>
     * --delay-seconds <delaySeconds>} to completion and returns its stdout.
     *
     * <p>{@code symbols} is written to a temporary file rather than passed as
     * command-line arguments — a batch can run into the thousands of symbols, well
     * past what's safe to pass as a single command line on any OS. The temp file is
     * always deleted afterward, whether the process succeeded, failed, or timed out.
     *
     * @param scriptPath    path to yfinance_wrapper.py
     * @param mode          "quarterly", "balance-sheet", or "closing-price"
     * @param symbols       already ".NS"-suffixed symbols for this batch
     * @param delaySeconds  pause between symbols inside the script (Yahoo politeness delay)
     * @return the script's full stdout content, or {@code null} if it failed to start,
     *         timed out, or exited with a non-zero status
     */
    public String runBatch(String scriptPath, String mode, List<String> symbols, double delaySeconds) {
        Path symbolsFile;
        try {
            symbolsFile = Files.createTempFile("yfinance-symbols-", ".txt");
            Files.write(symbolsFile, symbols);
        } catch (IOException e) {
            log.error("Failed to write symbols temp file for mode={}: {}", mode, e.getMessage());
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                pythonExecutable, scriptPath,
                "--mode", mode,
                "--symbols-file", symbolsFile.toString(),
                "--delay-seconds", String.valueOf(delaySeconds));

            Process process;
            try {
                process = pb.start();
            } catch (IOException e) {
                log.error("Failed to start Python process [{} {} --mode {}]: {}",
                    pythonExecutable, scriptPath, mode, e.getMessage());
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
                log.error("Interrupted while waiting for Python process, mode={}", mode);
                return null;
            }

            if (!finished) {
                process.destroyForcibly();
                log.error("Python process mode={} timed out after {}s for {} symbols — killed",
                    mode, timeoutSeconds, symbols.size());
                return null;
            }

            String out = stdout.join();
            String err = stderr.join();
            if (!err.isBlank()) {
                log.warn("Python process mode={} stderr:\n{}", mode, err);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Python process mode={} exited with code {}", mode, exitCode);
                return null;
            }

            return out;
        } finally {
            try {
                Files.deleteIfExists(symbolsFile);
            } catch (IOException e) {
                log.warn("Failed to delete symbols temp file {}: {}", symbolsFile, e.getMessage());
            }
        }
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