package com.example.transcription.dto;

import com.example.transcription.model.JobStatus;
import com.example.transcription.model.TranscriptionSegment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionResponse {
    private String id;
    private String originalFilename;
    private JobStatus status;
    private String text;
    private List<TranscriptionSegment> segments;
    private String errorMessage;
    private Instant createdAt;
    private Instant finishedAt;
}
