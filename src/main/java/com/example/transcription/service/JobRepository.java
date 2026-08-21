package com.example.transcription.service;

import com.example.transcription.model.TranscriptionJob;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory job store.
 * Replace with JPA / PostgreSQL in production.
 */
@Repository
public class JobRepository {

    private final Map<String, TranscriptionJob> jobs = new ConcurrentHashMap<>();

    public void save(TranscriptionJob job) {
        jobs.put(job.getId(), job);
    }

    public Optional<TranscriptionJob> findById(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public Collection<TranscriptionJob> findAll() {
        return jobs.values();
    }

    public void delete(String id) {
        jobs.remove(id);
    }
}
