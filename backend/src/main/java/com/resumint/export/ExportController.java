package com.resumint.export;

import com.resumint.web.ApiException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController @RequestMapping("/api/exports") @Profile("!worker")
public final class ExportController {
    private final ExportCoordinator coordinator; private final ExportStorage storage;
    public ExportController(ExportCoordinator coordinator,ExportStorage storage){this.coordinator=coordinator;this.storage=storage;}
    @PostMapping public ResponseEntity<ExportResponse> create(@RequestHeader("Idempotency-Key") String key,@RequestBody CreateExportRequest request){
        var submission=coordinator.submit(key,new ExportSnapshot(request.document()));
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).header(HttpHeaders.LOCATION,"/api/exports/"+submission.job().id()).body(ExportResponse.from(submission.job(),submission.credential()));
    }
    @GetMapping("/{id}") public ResponseEntity<ExportResponse> get(@PathVariable UUID id,@RequestHeader("X-Export-Credential") String credential){ return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ExportResponse.from(coordinator.authorized(id,credential),null)); }
    @DeleteMapping("/{id}") public ResponseEntity<ExportResponse> cancel(@PathVariable UUID id,@RequestHeader("X-Export-Credential") String credential){ return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ExportResponse.from(coordinator.cancel(id,credential),null)); }
    @GetMapping("/{id}/file") public ResponseEntity<FileSystemResource> file(@PathVariable UUID id,@RequestHeader("X-Export-Credential") String credential){
        ExportJob job=coordinator.authorized(id,credential);
        if(job.status()!=ExportStatus.SUCCEEDED || job.resultRef()==null) throw new ApiException(HttpStatus.CONFLICT,"EXPORT_NOT_READY","导出文件尚未就绪");
        if(!storage.exists(job.resultRef())) throw new ApiException(HttpStatus.GONE,"EXPORT_RESULT_EXPIRED","导出文件已过期");
        try { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=resume-"+id+".pdf").body(new FileSystemResource(storage.resultPath(job.resultRef()))); }
        catch(Exception e){ throw new ApiException(HttpStatus.GONE,"EXPORT_RESULT_EXPIRED","导出文件已过期"); }
    }
    public record CreateExportRequest(JsonNode document) { }
    public record ExportResponse(UUID id,String status,String credential,String errorCode,Instant createdAt,Instant resultExpiresAt){ static ExportResponse from(ExportJob job,String credential){return new ExportResponse(job.id(),job.status().name().toLowerCase(Locale.ROOT),credential,job.errorCode(),job.createdAt(),job.resultExpiresAt());}}
}
