package com.example.transcription.service;

import com.example.transcription.model.TranscriptionSegment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(SpeechToTextService.class);

    @Value("${app.vosk.model-path:models/vosk-model-small-en-us-0.15}")
    private String modelPath;

    private Model model;

    @PostConstruct
    public void init() throws IOException {
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        log.info("Loading Vosk model from: {}", modelPath);
        model = new Model(modelPath);
        log.info("Vosk model loaded successfully");
    }

    @PreDestroy
    public void cleanup() {
        if (model != null) {
            model.close();
        }
    }

    public TranscriptionResult transcribe(Path audioFile) {
        log.info("Transcribing with Vosk: {}", audioFile.getFileName());

        List<TranscriptionSegment> segments = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        try (Recognizer recognizer = new Recognizer(model, 16000.0F);
             FileInputStream fis = new FileInputStream(audioFile.toFile());
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            // Enable word-level timestamps so we can build segments
            recognizer.setWords(true);

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = bis.read(buffer)) >= 0) {
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    parsePartialResult(recognizer.getResult(), segments, fullText);
                }
            }

            // Final result
            parsePartialResult(recognizer.getFinalResult(), segments, fullText);

        } catch (Exception e) {
            throw new RuntimeException("Vosk transcription failed: " + e.getMessage(), e);
        }

        String text = fullText.toString().trim();
        log.info("Transcription finished. Length: {} chars, segments: {}", text.length(), segments.size());
        return new TranscriptionResult(text, segments);
    }

    private void parsePartialResult(String json, List<TranscriptionSegment> segments, StringBuilder fullText) {
        if (json == null || json.isBlank()) return;

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        // Prefer the "result" array (word-level) when available
        if (root.has("result") && root.get("result").isJsonArray()) {
            JsonArray words = root.getAsJsonArray("result");
            if (words.size() == 0) return;

            double start = words.get(0).getAsJsonObject().get("start").getAsDouble();
            double end = words.get(words.size() - 1).getAsJsonObject().get("end").getAsDouble();

            StringBuilder segmentText = new StringBuilder();
            for (int i = 0; i < words.size(); i++) {
                if (i > 0) segmentText.append(" ");
                segmentText.append(words.get(i).getAsJsonObject().get("word").getAsString());
            }

            String text = segmentText.toString();
            segments.add(new TranscriptionSegment(start, end, text));
            if (fullText.length() > 0) fullText.append(" ");
            fullText.append(text);
        }
        // Fallback to simple "text" field
        else if (root.has("text")) {
            String text = root.get("text").getAsString().trim();
            if (!text.isEmpty()) {
                segments.add(new TranscriptionSegment(0.0, 0.0, text));
                if (fullText.length() > 0) fullText.append(" ");
                fullText.append(text);
            }
        }
    }

    public record TranscriptionResult(String fullText, List<TranscriptionSegment> segments) {}
}