package com.example.transcription.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionJob {
    private String id;
    private String originalFilename;
    private String storedAudioPath;
    private JobStatus status;
    private String fullText;
    @Builder.Default
    private List<TranscriptionSegment> segments = new ArrayList<>();
    private String errorMessage;
    private int retryCount;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
}
