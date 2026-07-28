package com.conceptualware.api.rest;

import com.conceptualware.application.ConceptProgressApplicationService;
import com.conceptualware.domain.progress.ConceptProgress;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * Concept #25 — REST: expõe o bounded context de progresso do usuário
 * (dashboard de progresso no frontend consome estes endpoints).
 */
@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
public class ConceptProgressController {

    private final ConceptProgressApplicationService progressService;

    public record CompleteConceptRequest(@NotBlank String conceptSlug) {}

    @PostMapping("/complete")
    public ResponseEntity<ConceptProgress> completeConcept(
            Principal principal, @RequestBody CompleteConceptRequest request) {
        ConceptProgress progress = progressService.recordCompletion(principal.getName(), request.conceptSlug());
        return ResponseEntity.ok(progress);
    }

    @GetMapping("/me")
    public ResponseEntity<ConceptProgress> myProgress(Principal principal) {
        return ResponseEntity.ok(progressService.findOrCreate(principal.getName()));
    }

    @GetMapping("/me/eligibility")
    public ResponseEntity<Map<String, Boolean>> eligibility(Principal principal) {
        return ResponseEntity.ok(Map.of("eligibleForCertificate",
            progressService.isEligibleForCertificate(principal.getName())));
    }
}
