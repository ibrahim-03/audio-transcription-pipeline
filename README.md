# Audio Transcription Pipeline

A simple, production-oriented speech-to-text service built in **Java** with Spring Boot.

The system accepts audio files (WAV, MP3, M4A, etc.), normalizes them, transcribes spoken language into text (with segment-level timestamps), and exposes the functionality through a clean REST API.

---

## Design Goals

- Use only open-source speech-to-text engines
- Handle real-world audio formats and long files robustly
- Support concurrent uploads without overwhelming the server
- Provide clear job status, retry, and recovery mechanisms
- Keep the architecture simple and easy to extend

---

## High-Level Architecture

1. **API Layer** – Spring Boot REST controllers  
2. **Job Queue / Worker Pool** – bounded `ThreadPoolTaskExecutor`  
3. **Audio Normalization** – FFmpeg (16 kHz mono WAV)  
4. **Speech-to-Text Engine** – Mock (replaceable with Vosk / Whisper.cpp)  
5. **Storage**
   - Audio files → local filesystem (swap for S3 / MinIO in production)
   - Metadata & transcripts → in-memory store (swap for PostgreSQL)

---

## Core Pipeline

1. Client uploads an audio file via `POST /api/v1/transcriptions`
2. Service creates a job record with status `PENDING` and stores the original file
3. A worker picks up the job:
   - Converts the audio to 16 kHz mono WAV using FFmpeg
   - Runs the speech-to-text engine
   - Saves the full text + timed segments
   - Updates job status to `COMPLETED` (or `FAILED`)
4. Client polls `GET /api/v1/transcriptions/{id}` to retrieve the result

---

## Key Design Decisions

### Concurrent Uploads
- Uploads are accepted immediately and handed to a **bounded thread pool** (core 2, max 4 workers, queue 50).
- Excess requests are handled by the caller-runs policy or can be rejected with HTTP 429.
- This keeps CPU / memory usage predictable under load.

### Audio Format Handling
- Every file is normalized to **16 kHz, mono, 16-bit PCM WAV** using FFmpeg before transcription.
- Single conversion path supports MP3, WAV, M4A, OGG, FLAC, etc.
- If FFmpeg is not installed the original file is used (with a warning log).

### Long Audio Files
- In a real deployment the service would:
  - Split long files into overlapping 30–60 s chunks with FFmpeg
  - Transcribe chunks (optionally in parallel)
  - Stitch results using timestamps
  - Optionally apply Voice Activity Detection first
- The current mock processes the whole file; the architecture already supports async job handling for long-running work.

### Storage
- **Audio**: stored on disk under `./uploads` (configurable). Only the path is kept in the job record.
- **Transcripts & metadata**: held in an in-memory `ConcurrentHashMap` for simplicity.  
  Replace `JobRepository` with a JPA repository + PostgreSQL for production.

### Retry & Recovery
- Failed jobs are marked `FAILED` and the error is recorded.
- Manual retry endpoint: `POST /api/v1/transcriptions/{id}/retry`
- Maximum 3 retries enforced.
- Processing is idempotent – a job is never started twice if already `COMPLETED` or `PROCESSING`.

### API Design

| Method | Endpoint                          | Description                          |
|--------|-----------------------------------|--------------------------------------|
| POST   | `/api/v1/transcriptions`          | Upload audio → returns job ID (202)  |
| GET    | `/api/v1/transcriptions/{id}`     | Get status + transcript (or error)   |
| POST   | `/api/v1/transcriptions/{id}/retry` | Retry a failed job                 |
| DELETE | `/api/v1/transcriptions/{id}`     | Delete job and associated files      |

---

## Technology Stack

- **Language**: Java 17+
- **Framework**: Spring Boot 4.x
- **STT Engine**: Mock (easily replaceable with Vosk or Whisper.cpp)
- **Audio Processing**: FFmpeg
- **Build Tool**: Maven

---

## Prerequisites

- Java 17 or newer
- Maven 3.8+
- FFmpeg (optional but recommended) – install via your package manager

```bash
# Ubuntu / Debian
sudo apt install ffmpeg

# macOS
brew install ffmpeg
```

---

## Running Locally

```bash
# Clone the repo
git clone <your-repo-url>
cd transcription-pipeline

# Build & run
./mvnw spring-boot:run
```

The service starts on http://localhost:8080

### Example usage

```bash
# Upload an audio file
curl -X POST http://localhost:8080/api/v1/transcriptions \
  -F "file=@sample.mp3"

# Response (202 Accepted)
# {
#   "jobId": "a1b2c3d4-...",
#   "message": "Transcription job accepted",
#   "statusUrl": "/api/v1/transcriptions/a1b2c3d4-..."
# }

# Poll for result
curl http://localhost:8080/api/v1/transcriptions/a1b2c3d4-...
```

---

## Replacing the Mock STT Engine

Open `SpeechToTextService.java`.  
Replace the `transcribe()` method with a real implementation:

- **Vosk** – add the `vosk` Maven dependency and a model directory, then use `Recognizer`.
- **Whisper.cpp** – run the Whisper HTTP server locally and call it with a simple HTTP client.
- Any other open-source model exposed via HTTP / gRPC.

The rest of the pipeline (normalization, job handling, API) stays unchanged.

---

## Future Improvements

- Persistent database (PostgreSQL + Spring Data JPA)
- Object storage (S3 / MinIO)
- Authentication (API key or JWT)
- Webhook callbacks instead of polling
- Chunking + VAD for very long files
- Word-level timestamps and speaker diarization
- Horizontal scaling with a message broker (RabbitMQ / Redis)

---