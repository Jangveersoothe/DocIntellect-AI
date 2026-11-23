package com.jangveer.docintellect.controller;

import com.jangveer.docintellect.dto.AskRequest;
import com.jangveer.docintellect.dto.AskResponse;
import com.jangveer.docintellect.dto.ComplianceAnalysisRequest;
import com.jangveer.docintellect.dto.ComplianceAnalysisResult;
import com.jangveer.docintellect.service.DocumentAiService;
import com.jangveer.docintellect.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final DocumentAiService documentAiService;
    private final CurrentUser currentUser;

    public AiController(DocumentAiService documentAiService, CurrentUser currentUser) {
        this.documentAiService = documentAiService;
        this.currentUser = currentUser;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return documentAiService.ask(request.query(), currentUser.id());
    }

    @PostMapping("/compliance")
    public ComplianceAnalysisResult analyzeCompliance(@Valid @RequestBody ComplianceAnalysisRequest request) {
        return documentAiService.analyzeCompliance(request.query(), currentUser.id());
    }
}
