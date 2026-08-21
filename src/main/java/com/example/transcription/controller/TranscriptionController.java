package com.example.transcription.controller;

import com.example.transcription.dto.TranscriptionResponse;
import com.example.transcription.dto.UploadResponse;
import com.example.transcription.service.TranscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/transcriptions")
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    public TranscriptionController(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    /**
     * Upload an audio file and start a transcription job.
     * Returns 202 Accepted + job ID.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UploadResponse response = transcriptionService.submit(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Get job status and result (or error).
     */
    @GetMapping("/{id}")
    public TranscriptionResponse get(@PathVariable String id) {
        return transcriptionService.getJob(id);
    }

    /**
     * Manually retry a failed job.
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable String id) {
        transcriptionService.retry(id);
        return ResponseEntity.accepted().build();
    }

    /**
     * Delete a job and its stored audio.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        transcriptionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
