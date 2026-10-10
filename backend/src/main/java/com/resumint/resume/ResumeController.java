package com.resumint.resume;

import com.resumint.auth.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/resumes")
public final class ResumeController {
    private final ResumeService service;
    ResumeController(ResumeService service){this.service=service;}
    @GetMapping List<ResumeService.ResumeResponse> list(@AuthenticationPrincipal AuthPrincipal principal){return service.list(principal.userId());}
    @GetMapping("/{id}") ResumeService.ResumeResponse get(@AuthenticationPrincipal AuthPrincipal principal,@PathVariable UUID id){return service.get(principal.userId(),id);}
    @PostMapping ResponseEntity<ResumeService.ResumeResponse> create(@AuthenticationPrincipal AuthPrincipal principal,@Valid @RequestBody CreateRequest body){
        return ResponseEntity.status(201).body(service.create(principal.userId(),body.title(),body.templateId(),body.templateVersion(),body.schemaVersion(),body.document()));
    }
    @PutMapping("/{id}") ResumeService.ResumeResponse save(@AuthenticationPrincipal AuthPrincipal principal,@PathVariable UUID id,@Valid @RequestBody SaveRequest body){
        return service.save(principal.userId(),id,body.baseRevision(),body.title(),body.document());
    }
    @PatchMapping("/{id}/title") ResumeService.ResumeResponse rename(@AuthenticationPrincipal AuthPrincipal principal,@PathVariable UUID id,@Valid @RequestBody RenameRequest body){
        return service.rename(principal.userId(),id,body.baseRevision(),body.title());
    }
    @DeleteMapping("/{id}") ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,@PathVariable UUID id,@RequestParam long baseRevision){
        service.delete(principal.userId(),id,baseRevision);return ResponseEntity.noContent().build();
    }
    public record CreateRequest(@NotBlank String title,@NotBlank String templateId,@Positive int templateVersion,@NotBlank String schemaVersion,@NotNull JsonNode document){}
    public record SaveRequest(@Positive long baseRevision,@NotBlank String title,@NotNull JsonNode document){}
    public record RenameRequest(@Positive long baseRevision,@NotBlank String title){}
}
