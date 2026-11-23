package com.jangveer.docintellect;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(DocumentLifecycleIntegrationTest.TestModelConfiguration.class)
class DocumentLifecycleIntegrationTest {

    private static final int EMBEDDING_DIMENSIONS = 768;
    private static final String JWT_SECRET = "integration-only-secret-key-at-least-32-bytes";
    private static final Path UPLOAD_DIRECTORY = Path.of("target", "integration-uploads").toAbsolutePath();

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("docintellect")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VectorStore vectorStore;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.jwt.secret", () -> JWT_SECRET);
        registry.add("app.storage.upload-dir", () -> UPLOAD_DIRECTORY.toString());
    }

    @Test
    void authenticatesIndexesRetrievesAndDeletesOnlyWithinTheOwningTenant() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isUnauthorized());

        String ownerToken = registerAndLogin("owner@example.test");
        String otherUserToken = registerAndLogin("other@example.test");
        String searchableText = "tenant alpine confidential phrase";
        MockMultipartFile pdf = new MockMultipartFile(
                "file",
                "tenant-policy.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                createPdf(searchableText)
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(pdf)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(uploadResult.getResponse().getContentAsByteArray());
        UUID documentId = UUID.fromString(uploaded.get("id").asText());
        String storedFile = jdbcTemplate.queryForObject(
                "SELECT storage_path FROM document_metadata WHERE id = ?",
                String.class,
                documentId
        );

        awaitIndexed(ownerToken, documentId);

        Integer vectorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'documentId' = ? AND metadata->>'userId' = ?",
                Integer.class,
                documentId.toString(),
                userId(ownerToken)
        );
        assertTrue(vectorCount != null && vectorCount > 0, "Expected indexed chunks to be persisted in pgvector.");

        List<org.springframework.ai.document.Document> ownerMatches = search(searchableText, userId(ownerToken));
        assertTrue(ownerMatches.stream().anyMatch(match ->
                documentId.toString().equals(match.getMetadata().get("documentId"))));
        assertTrue(search(searchableText, userId(otherUserToken)).isEmpty(),
                "A different tenant must not retrieve this document's chunks.");

        mockMvc.perform(post("/api/v1/ai/ask")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.of("query", searchableText))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceDocumentIds[0]").value(documentId.toString()));
        mockMvc.perform(post("/api/v1/ai/ask")
                        .header("Authorization", bearer(otherUserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.of("query", searchableText))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceDocumentIds").isEmpty());

        mockMvc.perform(delete("/api/v1/documents/{id}", documentId)
                        .header("Authorization", bearer(otherUserToken)))
                .andExpect(status().isNotFound());

        assertTrue(Files.exists(UPLOAD_DIRECTORY.resolve(storedFile)));
        mockMvc.perform(delete("/api/v1/documents/{id}", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());

        Integer remainingVectors = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'documentId' = ?",
                Integer.class,
                documentId.toString()
        );
        assertEquals(0, remainingVectors);
        assertFalse(Files.exists(UPLOAD_DIRECTORY.resolve(storedFile)));
        assertTrue(search(searchableText, userId(ownerToken)).isEmpty());
        mockMvc.perform(get("/api/v1/documents/{id}", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
    }

    private String registerAndLogin(String email) throws Exception {
        String password = "integration-password-123";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.of(
                                "email", email.toUpperCase(Locale.ROOT),
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsByteArray())
                .get("accessToken")
                .asText();
    }

    private void awaitIndexed(String token, UUID documentId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/documents/{id}", documentId)
                            .header("Authorization", bearer(token)))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                JsonNode document = objectMapper.readTree(result.getResponse().getContentAsByteArray());
                String status = document.get("status").asText();
                if ("INDEXED".equals(status)) {
                    return;
                }
                if ("FAILED".equals(status)) {
                    throw new AssertionError("PDF indexing failed: " + document);
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for PDF indexing.");
    }

    private List<Document> search(String query, String userId) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(5)
                .filterExpression(new Filter.Expression(
                        Filter.ExpressionType.EQ,
                        new Filter.Key("userId"),
                        new Filter.Value(userId)
                ))
                .build());
    }

    private String userId(String token) {
        String[] parts = token.split("\\.");
        try {
            JsonNode claims = objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(parts[1]));
            return claims.get("sub").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Test JWT did not contain a valid subject.", exception);
        }
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private byte[] createPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(25, 700);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestModelConfiguration {

        @Bean
        @Primary
        EmbeddingModel deterministicEmbeddingModel() {
            return new EmbeddingModel() {
                @Override
                public EmbeddingResponse call(EmbeddingRequest request) {
                    return new EmbeddingResponse(IntStream.range(0, request.getInstructions().size())
                            .mapToObj(index -> new Embedding(
                                    embedding(request.getInstructions().get(index)),
                                    index
                            ))
                            .toList());
                }

                @Override
                public float[] embed(Document document) {
                    return embedding(document.getText());
                }

                @Override
                public int dimensions() {
                    return EMBEDDING_DIMENSIONS;
                }
            };
        }

        @Bean
        @Primary
        ChatModel deterministicChatModel() {
            return prompt -> new ChatResponse(List.of(
                    new Generation(new AssistantMessage("Integration test answer."))
            ));
        }

        private static float[] embedding(String text) {
            float[] vector = new float[EMBEDDING_DIMENSIONS];
            for (String token : text.toLowerCase(Locale.ROOT).split("\\W+")) {
                if (!token.isBlank()) {
                    int hash = token.hashCode();
                    int index = Math.floorMod(hash, vector.length);
                    vector[index] += (hash & 1) == 0 ? 1 : -1;
                }
            }
            return vector;
        }
    }
}
