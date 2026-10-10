package com.resumint.resume;

import com.resumint.export.ResumeDocumentSchema;
import com.resumint.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
    ResumeService(ResumeRepository repository,ResumeDocumentSchema schema,ObjectMapper objectMapper,Clock clock){
        this.repository=repository;this.schema=schema;this.objectMapper=objectMapper;this.clock=clock;
    }
    List<ResumeResponse> list(UUID userId){return repository.list(userId).stream().map(row->response(row,false)).toList();}
    ResumeResponse get(UUID userId,UUID id){return response(require(userId,id),true);}

    @Transactional ResumeResponse create(UUID userId,String title,String templateId,int templateVersion,String schemaVersion,JsonNode document){
        validate(title,templateId,templateVersion,schemaVersion,document);
        Instant now=clock.instant();
        ResumeRepository.ResumeRow row=new ResumeRepository.ResumeRow(UUID.randomUUID(),userId,title.trim(),templateId,templateVersion,schemaVersion,
                write(document),1,now,now);
        repository.insert(row);return response(row,true);
    }

    @Transactional ResumeResponse save(UUID userId,UUID id,long baseRevision,String title,JsonNode document){
        ResumeRepository.ResumeRow current=require(userId,id);
        validate(title,current.templateId(),current.templateVersion(),current.schemaVersion(),document);
        Instant now=clock.instant();
        if(repository.update(userId,id,baseRevision,title.trim(),write(document),now)==0)throw conflict(userId,id);
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

    private void validate(String title,String templateId,int version,String schemaVersion,JsonNode document){
        title(title);
        if(templateId==null||templateId.isBlank()||version<1||!repository.templateVersionExists(templateId,version))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_TEMPLATE_VERSION","模板版本不可用于保存");
        try{schema.validateTopLevel(document);}catch(IllegalArgumentException exception){
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_RESUME_DOCUMENT","简历内容不符合当前结构");
        }
        if(!schemaVersion.equals(document.path("schemaVersion").asString()))
            throw new ApiException(HttpStatus.BAD_REQUEST,"SCHEMA_VERSION_MISMATCH","简历结构版本不匹配");
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
