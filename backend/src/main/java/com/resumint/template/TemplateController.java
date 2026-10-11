package com.resumint.template;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/templates")
public final class TemplateController {
    private final TemplateMapper mapper;
    private final ObjectMapper objectMapper;
    public TemplateController(TemplateMapper mapper,ObjectMapper objectMapper) { this.mapper=mapper; this.objectMapper=objectMapper; }
    @GetMapping public ResponseEntity<List<TemplateResponse>> list() { return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(mapper.findPublic().stream().map(this::response).toList()); }
    @GetMapping("/{id}") public ResponseEntity<TemplateResponse> get(@PathVariable String id) {
        TemplateMapper.TemplateRecord row=mapper.findPublicById(id);
        if(row==null) throw new com.resumint.web.ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"TEMPLATE_NOT_FOUND","模板不存在或不可用于新建");
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(response(row));
    }
    @GetMapping("/{id}/versions/{version}") public ResponseEntity<TemplateResponse> version(@PathVariable String id,@PathVariable int version) {
        TemplateMapper.TemplateRecord row=mapper.findPublishedVersion(id,version);
        if(row==null) throw new com.resumint.web.ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"TEMPLATE_VERSION_NOT_FOUND","模板版本不存在");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable()).body(response(row));
    }
    private TemplateResponse response(TemplateMapper.TemplateRecord row) {
        return new TemplateResponse(row.id(),row.name(),read(row.tags()),row.version(),row.schemaVersion(),read(row.configJson()));
    }
    private JsonNode read(String value) { try { return objectMapper.readTree(value); } catch(Exception e) { throw new IllegalStateException("invalid published template json",e); } }
    public record TemplateResponse(String id,String name,JsonNode tags,int version,String schemaVersion,JsonNode config) { }
}
