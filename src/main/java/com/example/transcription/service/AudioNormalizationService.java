package com.example.transcription.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Normalizes any supported audio format to 16 kHz mono 16-bit PCM WAV
 * using FFmpeg. This is the single conversion path for all input formats.
 */
@Service
public class AudioNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(AudioNormalizationService.class);

    /**
     * Converts the given audio file to a standardized WAV.
     * Returns the path of the normalized file.
     * If FFmpeg is not available, returns the original path (with a warning).
     */
    public Path normalize(Path input) throws IOException, InterruptedException {
        if (!isFfmpegAvailable()) {
            log.warn("FFmpeg not found on PATH – skipping normalization. " +
                    "Install FFmpeg for production use.");
            return input;
        }

        Path output = input.resolveSibling(input.getFileName().toString() + ".normalized.wav");

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",                   // overwrite
                "-i", input.toString(),
                "-ar", "16000",         // 16 kHz
                "-ac", "1",             // mono
                "-c:a", "pcm_s16le",    // 16-bit PCM
                output.toString()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        // consume output to avoid blocking
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("ffmpeg: {}", line);
            }
        }

        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("FFmpeg timed out while normalizing " + input);
        }
        if (process.exitValue() != 0) {
            throw new IOException("FFmpeg failed with exit code " + process.exitValue());
        }

        log.info("Normalized {} → {}", input.getFileName(), output.getFileName());
        return output;
    }

    private boolean isFfmpegAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void cleanup(Path normalized) {
        try {
            if (normalized != null && normalized.toString().endsWith(".normalized.wav")) {
                Files.deleteIfExists(normalized);
            }
        } catch (IOException ignored) {
        }
    }
}
