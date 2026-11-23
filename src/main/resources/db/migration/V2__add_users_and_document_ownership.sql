CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_app_user_email_lower
    ON app_user (LOWER(email));

ALTER TABLE document_metadata
    ADD COLUMN user_id UUID;

ALTER TABLE document_metadata
    ADD CONSTRAINT fk_document_metadata_user
        FOREIGN KEY (user_id) REFERENCES app_user (id);

ALTER TABLE document_metadata
    ADD CONSTRAINT ck_document_metadata_user_id_not_null
        CHECK (user_id IS NOT NULL) NOT VALID;

CREATE INDEX idx_document_metadata_user_status_uploaded_at
    ON document_metadata (user_id, status, uploaded_at DESC);
