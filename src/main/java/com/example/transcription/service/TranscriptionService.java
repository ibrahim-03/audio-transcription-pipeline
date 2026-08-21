package com.example.transcription.service;

import com.example.transcription.dto.TranscriptionResponse;
import com.example.transcription.dto.UploadResponse;
import com.example.transcription.exception.JobNotFoundException;
import com.example.transcription.model.JobStatus;
import com.example.transcription.model.TranscriptionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Service
public class TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionService.class);
    private static final int MAX_RETRIES = 3;

    private final StorageService storageService;
    private final AudioNormalizationService normalizationService;
    private final SpeechToTextService sttService;
    private final JobRepository jobRepository;

    public TranscriptionService(StorageService storageService,
                                AudioNormalizationService normalizationService,
                                SpeechToTextService sttService,
                                JobRepository jobRepository) {
        this.storageService = storageService;
        this.normalizationService = normalizationService;
        this.sttService = sttService;
        this.jobRepository = jobRepository;
    }

    /**
     * Accepts an upload, creates a job, and kicks off async processing.
     */
    public UploadResponse submit(MultipartFile file) throws Exception {
        String jobId = UUID.randomUUID().toString();
        String storedPath = storageService.store(file);

        TranscriptionJob job = TranscriptionJob.builder()
                .id(jobId)
                .originalFilename(file.getOriginalFilename())
                .storedAudioPath(storedPath)
                .status(JobStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        jobRepository.save(job);
        processAsync(jobId);

        return UploadResponse.builder()
                .jobId(jobId)
                .message("Transcription job accepted")
                .statusUrl("/api/v1/transcriptions/" + jobId)
                .build();
    }

    @Async("transcriptionExecutor")
    public void processAsync(String jobId) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        job.setStatus(JobStatus.PROCESSING);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);

        Path normalized = null;
        try {
            Path original = storageService.resolve(job.getStoredAudioPath());
            normalized = normalizationService.normalize(original);

            SpeechToTextService.TranscriptionResult result = sttService.transcribe(normalized);

            job.setFullText(result.fullText());
            job.setSegments(result.segments());
            job.setStatus(JobStatus.COMPLETED);
            job.setFinishedAt(Instant.now());
            job.setErrorMessage(null);

            log.info("Job {} completed successfully", jobId);
        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setFinishedAt(Instant.now());
            job.setRetryCount(job.getRetryCount() + 1);
        } finally {
            normalizationService.cleanup(normalized);
            jobRepository.save(job);
        }
    }

    public TranscriptionResponse getJob(String id) {
        TranscriptionJob job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));

        return TranscriptionResponse.builder()
                .id(job.getId())
                .originalFilename(job.getOriginalFilename())
                .status(job.getStatus())
                .text(job.getFullText())
                .segments(job.getSegments())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .finishedAt(job.getFinishedAt())
                .build();
    }

    /**
     * Simple retry for failed jobs (called by scheduler or manually).
     */
    public void retry(String id) {
        TranscriptionJob job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));

        if (job.getStatus() != JobStatus.FAILED) {
            throw new IllegalStateException("Only FAILED jobs can be retried");
        }
        if (job.getRetryCount() >= MAX_RETRIES) {
            throw new IllegalStateException("Maximum retries (" + MAX_RETRIES + ") exceeded");
        }

        job.setStatus(JobStatus.PENDING);
        job.setErrorMessage(null);
        jobRepository.save(job);
        processAsync(id);
    }

    public void delete(String id) {
        TranscriptionJob job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));
        storageService.delete(job.getStoredAudioPath());
        jobRepository.delete(id);
    }
}
