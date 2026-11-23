# DocIntellect AI

DocIntellect AI is a Java 21 / Spring Boot backend for PDF ingestion, metadata management, and grounded document analysis using local Ollama models. PostgreSQL stores relational metadata and pgvector embeddings.

## Architecture

```text
REST API
  ├── Document API → local PDF storage → PDFBox extraction → token chunker
  │                                                   └── Ollama embeddings → PostgreSQL / pgvector
  └── AI API → semantic retrieval → grounded Ollama chat response
                                  └── metadata lookup tool → PostgreSQL
```

Uploaded PDFs are capped at 10 MB. Their metadata moves from `UPLOADED` to `INDEXED` after successful extraction and embedding, or to `FAILED` if ingestion fails. API errors use RFC 7807 `ProblemDetail` responses.

## Tech stack

- Java 21, Spring Boot 4.1.2, Spring AI 2.0.2, Maven
- PostgreSQL 16 with pgvector and Flyway-managed relational schema migrations
- Ollama chat (`llama3.2`) and embeddings (`nomic-embed-text`)
- Apache PDFBox for PDF text extraction
- Spring Boot Actuator for health and metrics

## Quickstart

### Recommended: run everything with Docker Compose

1. Copy `.env.example` to `.env` and set a non-default database password.
2. Start PostgreSQL, Ollama, model initialization, and the API:

   ```sh
   docker compose up -d
   ```

The application is available at `http://localhost:8080`. Compose pulls `llama3.2` and `nomic-embed-text`, starts pgvector, and builds the Spring Boot image. Model downloads are large and first startup may take several minutes; Ollama also needs sufficient RAM (and GPU acceleration is recommended).

### Run locally with Ollama

1. Start PostgreSQL with `docker compose up -d postgres`.
2. Install [Ollama](https://ollama.com/) and download the models:

   ```sh
   ollama pull llama3.2
   ollama pull nomic-embed-text
   ```

3. Run the application:

   ```sh
   ./mvnw spring-boot:run
   ```

   On Windows, use `mvnw.cmd spring-boot:run`.

The API listens on `http://localhost:8080`. Flyway applies relational database migrations and Hibernate validates the mapped schema at startup. Spring AI initializes the pgvector store schema. Uploaded files are stored under `uploads/` and persisted through a Docker bind mount.

## API

Upload a PDF (indexing happens asynchronously; the response initially reports `UPLOADED`):

```sh
curl -F "file=@./example.pdf" http://localhost:8080/api/v1/documents/upload
```

Upload multiple PDFs with repeated `files` form fields. A batch supports up to 10 files, each limited to 10 MB. Results are reported independently for each file:

```sh
curl -F "files=@./first.pdf" -F "files=@./second.pdf" \
  http://localhost:8080/api/v1/documents/upload/batch
```

Poll the returned document UUID until its status is `INDEXED` or `FAILED`. The same endpoint returns the current status:

```sh
curl http://localhost:8080/api/v1/documents
curl http://localhost:8080/api/v1/documents/{id}
```

`GET /api/v1/documents/{id}` is the current indexing-status endpoint. The in-process worker uses a bounded thread pool and retries indexing up to three times. This is still single-instance processing; a durable broker/outbox is required for multi-replica production deployment.

Delete a document after upload. This removes its pgvector chunks, local PDF, and relational metadata; a successful request returns `204 No Content`:

```sh
curl -X DELETE http://localhost:8080/api/v1/documents/{id}
```

Delete multiple documents by sending their IDs in a JSON body. A batch supports up to 100 IDs and reports each result independently:

```sh
curl -X DELETE http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/json" \
  -d '{"ids":["<first-document-id>","<second-document-id>"]}'
```

Ask a grounded question after indexing completes. The response includes source document IDs and page/chunk citations. Older vectors created before page metadata was added may be returned without page citations and should be re-indexed:

```sh
curl -H "Content-Type: application/json" \
  -d '{"query":"What are the termination conditions?"}' \
  http://localhost:8080/api/v1/ai/ask
```

Request structured compliance analysis:

```sh
curl -H "Content-Type: application/json" \
  -d '{"query":"Assess the indexed policy against its stated requirements."}' \
  http://localhost:8080/api/v1/ai/compliance
```

The AI can also use an annotated tool to query recent document metadata by status. Health and metrics are available at `/actuator/health` and `/actuator/metrics`.

## Configuration

All secrets and deployment-specific values are supplied through environment variables; `.env` is ignored by Git. See `.env.example` and `src/main/resources/application.yml`.

Relational schema changes belong in `src/main/resources/db/migration` as new, ordered Flyway migrations. Do not edit a migration after it has been applied to a shared database. If upgrading an existing database created by an earlier prototype version, baseline it deliberately before deploying the Flyway-enabled application.

The chunker uses whitespace-delimited tokens with configurable chunk size and overlap, preserving PDF page numbers and stable chunk IDs for citations. It is not a model-specific tokenizer; precise token budgets and layout-aware parsing remain production enhancements.

## Development

```sh
./mvnw test
```

The ingestion worker is in-process and suitable for local development; it is not a durable queue and should not be treated as a multi-replica production job system.
