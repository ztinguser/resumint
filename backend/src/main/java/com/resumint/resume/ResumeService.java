package com.resumint.resume;

import com.resumint.export.ResumeDocumentSchema;
import com.resumint.export.ResumeDocumentResourcePolicy;
import com.resumint.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ResumeService {
    private final ResumeRepository repository;
    private final ResumeDocumentSchema schema;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int maxDocumentBytes;
    ResumeService(ResumeRepository repository,ResumeDocumentSchema schema,ObjectMapper objectMapper,Clock clock,
            @Value("${resumint.resume.max-document-bytes}") int maxDocumentBytes){
        if(maxDocumentBytes<1)throw new IllegalArgumentException("resume document limit must be positive");
        this.repository=repository;this.schema=schema;this.objectMapper=objectMapper;this.clock=clock;this.maxDocumentBytes=maxDocumentBytes;
    }
    List<ResumeResponse> list(UUID userId){return repository.list(userId).stream().map(row->response(row,false)).toList();}
    ResumeResponse get(UUID userId,UUID id){return response(require(userId,id),true);}

    @Transactional ResumeResponse create(UUID userId,String title,String templateId,int templateVersion,String schemaVersion,JsonNode document){
        String encoded=validate(title,templateId,templateVersion,schemaVersion,document);
        Instant now=clock.instant();
        ResumeRepository.ResumeRow row=new ResumeRepository.ResumeRow(UUID.randomUUID(),userId,title.trim(),templateId,templateVersion,schemaVersion,
                encoded,1,now,now);
        repository.insert(row);return response(row,true);
    }

    @Transactional ResumeResponse save(UUID userId,UUID id,long baseRevision,String title,JsonNode document){
        ResumeRepository.ResumeRow current=require(userId,id);
        String encoded=validate(title,current.templateId(),current.templateVersion(),current.schemaVersion(),document);
        Instant now=clock.instant();
        if(repository.update(userId,id,baseRevision,title.trim(),encoded,now)==0)throw conflict(userId,id);
        return response(require(userId,id),true);
    }

    @Transactional ResumeResponse rename(UUID userId,UUID id,long baseRevision,String title){
        title(title);
        require(userId,id);
        if(repository.rename(userId,id,baseRevision,title.trim(),clock.instant())==0)throw conflict(userId,id);
        return response(require(userId,id),true);
    }

    @Transactional void delete(UUID userId,UUID id,long baseRevision){
        require(userId,id);
        if(repository.delete(userId,id,baseRevision)==0)throw conflict(userId,id);
    }

    private String validate(String title,String templateId,int version,String schemaVersion,JsonNode document){
        title(title);
        if(templateId==null||templateId.isBlank()||version<1||!repository.templateVersionExists(templateId,version))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_TEMPLATE_VERSION","模板版本不可用于保存");
        String encoded=write(document);
        int bytes=encoded.getBytes(StandardCharsets.UTF_8).length;
        if(bytes>maxDocumentBytes)throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"RESUME_LIMIT_EXCEEDED","简历内容超过保存限制",Map.of("limit","maxDocumentBytes","allowed",maxDocumentBytes,"actual",bytes));
        try{schema.validateTopLevel(document);ResumeDocumentResourcePolicy.validate(document);}catch(IllegalArgumentException exception){
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_RESUME_DOCUMENT","简历内容不符合当前结构");
        }
        if(!schemaVersion.equals(document.path("schemaVersion").asString()))
            throw new ApiException(HttpStatus.BAD_REQUEST,"SCHEMA_VERSION_MISMATCH","简历结构版本不匹配");
        return encoded;
    }
    private static void title(String title){if(title==null||title.trim().isEmpty()||title.trim().length()>120)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_RESUME_TITLE","简历名称需为 1–120 个字符");}
    private ResumeRepository.ResumeRow require(UUID userId,UUID id){return repository.find(userId,id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESUME_NOT_FOUND","简历不存在"));}
    private ApiException conflict(UUID userId,UUID id){
        ResumeRepository.ResumeRow current=require(userId,id);
        return new ApiException(HttpStatus.CONFLICT,"REVISION_CONFLICT","服务器上的简历已被更新，本地内容未覆盖服务器版本",
                Map.of("currentRevision",current.revision(),"currentUpdatedAt",current.updatedAt(),"currentTitle",current.title()));
    }
    private String write(JsonNode document){try{return objectMapper.writeValueAsString(document);}catch(Exception exception){throw new IllegalArgumentException(exception);}}
    private ResumeResponse response(ResumeRepository.ResumeRow row,boolean includeDocument){
        JsonNode document=null;if(includeDocument){try{document=objectMapper.readTree(row.documentJson());}catch(Exception exception){throw new IllegalStateException(exception);}}
        return new ResumeResponse(row.id(),row.title(),row.templateId(),row.templateVersion(),row.schemaVersion(),document,row.revision(),row.createdAt(),row.updatedAt());
    }
    record ResumeResponse(UUID id,String title,String templateId,int templateVersion,String schemaVersion,JsonNode document,long revision,Instant createdAt,Instant updatedAt){}
}
