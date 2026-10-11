package com.resumint.template;

import com.resumint.auth.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/templates")
public class AdminTemplateController {
    private final AdminTemplateService service;
    AdminTemplateController(AdminTemplateService service){this.service=service;}

    @GetMapping List<AdminTemplateService.TemplateSummary> list(){return service.list();}
    @GetMapping("/{id}") AdminTemplateService.TemplateDraft get(@PathVariable String id){return service.get(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    AdminTemplateService.TemplateDraft create(@AuthenticationPrincipal AuthPrincipal principal,@Valid @RequestBody AdminTemplateService.CreateTemplate body){return service.create(principal.userId(),body);}
    @PutMapping("/{id}/draft") AdminTemplateService.TemplateDraft save(@AuthenticationPrincipal AuthPrincipal principal,@PathVariable String id,@Valid @RequestBody AdminTemplateService.SaveDraft body){return service.save(principal.userId(),id,body);}
    @PostMapping("/{id}/publish") AdminTemplateService.PublishedVersion publish(@AuthenticationPrincipal AuthPrincipal principal,@PathVariable String id,@Valid @RequestBody PublishRequest body){return service.publish(principal.userId(),id,body.expectedRevision());}
    @PutMapping("/{id}/name") AdminTemplateService.TemplateDraft rename(@AuthenticationPrincipal AuthPrincipal principal,@PathVariable String id,@Valid @RequestBody RenameRequest body){return service.rename(principal.userId(),id,body.displayName());}
    @PutMapping("/{id}/listing") AdminTemplateService.TemplateDraft listing(@AuthenticationPrincipal AuthPrincipal principal,@PathVariable String id,@Valid @RequestBody ListingRequest body){return service.listing(principal.userId(),id,body.listed(),body.version());}
    @GetMapping("/{id}/audit") List<AdminTemplateService.AuditEvent> audit(@PathVariable String id){return service.audit(id);}

    public record PublishRequest(long expectedRevision){}
    public record RenameRequest(@NotBlank String displayName){}
    public record ListingRequest(boolean listed,Integer version){}
}
