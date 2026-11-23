package com.jangveer.docintellect.service;

import com.jangveer.docintellect.config.BootstrapOwnerProperties;
import com.jangveer.docintellect.entity.AppUser;
import com.jangveer.docintellect.repository.AppUserRepository;
import com.jangveer.docintellect.repository.DocumentMetadataRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class LegacyDocumentOwnershipInitializer implements ApplicationRunner {

    private static final String VALIDATE_OWNER_CONSTRAINT =
            "ALTER TABLE document_metadata VALIDATE CONSTRAINT ck_document_metadata_user_id_not_null";

    private final DocumentMetadataRepository documentRepository;
    private final AppUserRepository userRepository;
    private final BootstrapOwnerProperties bootstrapOwner;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public LegacyDocumentOwnershipInitializer(
            DocumentMetadataRepository documentRepository,
            AppUserRepository userRepository,
            BootstrapOwnerProperties bootstrapOwner,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate
    ) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.bootstrapOwner = bootstrapOwner;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<com.jangveer.docintellect.entity.DocumentMetadata> legacyDocuments =
                documentRepository.findAllByUserIdIsNull();
        boolean hasEmail = hasText(bootstrapOwner.email());
        boolean hasPassword = hasText(bootstrapOwner.password());

        if (hasEmail != hasPassword) {
            throw new IllegalStateException(
                    "Set both BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD, or leave both empty."
            );
        }
        if (legacyDocuments.isEmpty()) {
            jdbcTemplate.execute(VALIDATE_OWNER_CONSTRAINT);
            return;
        }
        if (!hasEmail) {
            throw new IllegalStateException(
                    "Existing documents have no owner. Set BOOTSTRAP_ADMIN_EMAIL and "
                            + "BOOTSTRAP_ADMIN_PASSWORD to assign them to a bootstrap account."
            );
        }
        validateBootstrapCredentials();

        String normalizedEmail = bootstrapOwner.email().trim().toLowerCase(Locale.ROOT);
        AppUser owner = userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> userRepository.saveAndFlush(new AppUser(
                        normalizedEmail,
                        passwordEncoder.encode(bootstrapOwner.password())
                )));

        for (var document : legacyDocuments) {
            attachUserToVectors(document.getId(), owner.getId());
            document.setUserId(owner.getId());
            documentRepository.save(document);
        }
        documentRepository.flush();
        jdbcTemplate.execute(VALIDATE_OWNER_CONSTRAINT);
    }

    private void attachUserToVectors(UUID documentId, UUID userId) {
        String metadataType = jdbcTemplate.queryForObject(
                """
                        SELECT data_type
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'vector_store'
                          AND column_name = 'metadata'
                        """,
                String.class
        );
        if (!"json".equals(metadataType) && !"jsonb".equals(metadataType)) {
            throw new IllegalStateException("The pgvector metadata column must use JSON or JSONB.");
        }

        String jsonCast = "json".equals(metadataType) ? "::json" : "";
        String sql = """
                UPDATE vector_store
                SET metadata = jsonb_set(metadata::jsonb, '{userId}', to_jsonb(CAST(? AS text)), true)%s
                WHERE metadata->>'documentId' = ?
                """.formatted(jsonCast);
        jdbcTemplate.update(sql, userId.toString(), documentId.toString());
    }

    private void validateBootstrapCredentials() {
        if (bootstrapOwner.password().getBytes(StandardCharsets.UTF_8).length < 12
                || bootstrapOwner.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must be between 12 and 72 UTF-8 bytes."
            );
        }
        if (!bootstrapOwner.email().contains("@")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL must be a valid email address.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
