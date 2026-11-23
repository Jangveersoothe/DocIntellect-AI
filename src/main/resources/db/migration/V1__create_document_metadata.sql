CREATE TABLE document_metadata (
    id UUID PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size >= 0),
    storage_path VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('UPLOADED', 'INDEXED', 'FAILED')),
    uploaded_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_document_metadata_status_uploaded_at
    ON document_metadata (status, uploaded_at DESC);
