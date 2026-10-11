package com.resumint.template;

import com.resumint.web.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdminTemplateService {
    private static final Pattern STABLE_ID = Pattern.compile("^[a-z][a-z0-9-]{2,63}$");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TemplateConfigValidator validator;
    private final Clock clock;

    AdminTemplateService(JdbcTemplate jdbc, ObjectMapper json, TemplateConfigValidator validator, Clock clock) {
        this.jdbc = jdbc; this.json = json; this.validator = validator; this.clock = clock;
    }

    public List<TemplateSummary> list() {
        return jdbc.query("""
                SELECT t.id,t.display_name,CAST(t.tags AS CHAR) tags,t.listed,t.current_version,
                  d.base_version,d.revision,d.updated_at
                FROM templates t LEFT JOIN template_drafts d ON d.template_id=t.id ORDER BY t.created_at
                """, (rs, row) -> new TemplateSummary(rs.getString("id"), rs.getString("display_name"),
                        read(rs.getString("tags")), rs.getBoolean("listed"), nullableInt(rs,"current_version"),
                        nullableInt(rs,"base_version"), nullableLong(rs,"revision"), instant(rs,"updated_at")));
    }

    public TemplateDraft get(String id) {
        return jdbc.query("""
                SELECT t.id,t.display_name,CAST(t.tags AS CHAR) tags,t.listed,t.current_version,
                  d.base_version,d.schema_version,CAST(d.config_json AS CHAR) config_json,
                  CAST(d.component_versions AS CHAR) component_versions,CAST(d.asset_manifest AS CHAR) asset_manifest,
                  d.revision,d.updated_at
                FROM templates t LEFT JOIN template_drafts d ON d.template_id=t.id WHERE t.id=?
                """, (rs,row) -> draft(rs), id).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"TEMPLATE_NOT_FOUND","模板不存在"));
    }

    @Transactional
    public TemplateDraft create(UUID actor, CreateTemplate request) {
        String id = request.id().strip().toLowerCase(Locale.ROOT);
        String name = validateName(request.displayName());
        if (!STABLE_ID.matcher(id).matches()) throw bad("TEMPLATE_ID_INVALID","稳定模板 ID 只能使用小写字母、数字和连字符");
        try {
            jdbc.update("INSERT INTO templates(id,display_name,tags,listed,current_version) VALUES(?,?,CAST(? AS JSON),FALSE,NULL)",
                    id,name,write(request.tags()));
            JsonNode initial = request.initialConfig()==null ? json.createObjectNode()
                    .set("layoutTree",json.createObjectNode().set("children",json.createArrayNode())) : request.initialConfig();
            jdbc.update("INSERT INTO template_drafts(template_id,base_version,schema_version,config_json,component_versions,asset_manifest,revision,updated_by,updated_at) VALUES(?,NULL,'1.0',CAST(? AS JSON),JSON_OBJECT(),JSON_ARRAY(),1,?,?)",
                    id,write(initial),actor.toString(),Timestamp.from(clock.instant()));
            audit(id,actor,"CREATED",Map.of("displayName",name));
        } catch (DuplicateKeyException duplicate) {
            throw new ApiException(HttpStatus.CONFLICT,"TEMPLATE_ID_OR_NAME_EXISTS","模板稳定 ID 或展示名已存在");
        }
        return get(id);
    }

    @Transactional
    public TemplateDraft save(UUID actor, String id, SaveDraft request) {
        requireTemplate(id);
        List<String> errors = validator.validate(request.config(),request.componentVersions(),request.assetManifest());
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"TEMPLATE_CONFIG_INVALID",String.join("；",errors));
        int changed = jdbc.update("""
                UPDATE template_drafts SET schema_version=?,config_json=CAST(? AS JSON),component_versions=CAST(? AS JSON),
                  asset_manifest=CAST(? AS JSON),revision=revision+1,updated_by=?,updated_at=?
                WHERE template_id=? AND revision=?
                """, request.schemaVersion(),write(request.config()),write(request.componentVersions()),write(request.assetManifest()),
                actor.toString(),Timestamp.from(clock.instant()),id,request.expectedRevision());
        if (changed==0) throw new ApiException(HttpStatus.CONFLICT,"TEMPLATE_DRAFT_CONFLICT","模板草稿已被其他操作更新，请重新加载");
        audit(id,actor,"DRAFT_SAVED",Map.of("previousRevision",request.expectedRevision()));
        return get(id);
    }

    @Transactional
    public PublishedVersion publish(UUID actor, String id, long expectedRevision) {
        TemplateDraft draft=get(id);
        if (draft.revision()==null || draft.revision()!=expectedRevision) throw new ApiException(HttpStatus.CONFLICT,"TEMPLATE_DRAFT_CONFLICT","模板草稿版本已变化");
        validateName(draft.displayName());
        List<String> errors=validator.validate(draft.config(),draft.componentVersions(),draft.assetManifest());
        if(!errors.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"TEMPLATE_CONFIG_INVALID",String.join("；",errors));
        Integer next=jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM template_versions WHERE template_id=?",Integer.class,id);
        Instant now=clock.instant();
        jdbc.update("INSERT INTO template_versions(template_id,version,schema_version,config_json,component_versions,asset_manifest,status,published_at) VALUES(?,?,?,CAST(? AS JSON),CAST(? AS JSON),CAST(? AS JSON),'PUBLISHED',?)",
                id,next,draft.schemaVersion(),write(draft.config()),write(draft.componentVersions()),write(draft.assetManifest()),Timestamp.from(now));
        jdbc.update("UPDATE template_drafts SET base_version=?,revision=revision+1,updated_by=?,updated_at=? WHERE template_id=? AND revision=?",
                next,actor.toString(),Timestamp.from(now),id,expectedRevision);
        audit(id,actor,"VERSION_PUBLISHED",Map.of("version",next));
        return new PublishedVersion(id,next,now);
    }

    @Transactional
    public TemplateDraft rename(UUID actor,String id,String displayName) {
        String name=validateName(displayName); requireTemplate(id);
        try { jdbc.update("UPDATE templates SET display_name=? WHERE id=?",name,id); }
        catch(DuplicateKeyException duplicate){ throw new ApiException(HttpStatus.CONFLICT,"TEMPLATE_NAME_EXISTS","展示名已被其他模板使用"); }
        audit(id,actor,"RENAMED",Map.of("displayName",name)); return get(id);
    }

    @Transactional
    public TemplateDraft listing(UUID actor,String id,boolean listed,Integer version) {
        requireTemplate(id);
        if(listed){
            if(version==null || !published(id,version)) throw bad("TEMPLATE_VERSION_NOT_PUBLISHED","上架必须指向明确的已发布版本");
            jdbc.update("UPDATE templates SET listed=TRUE,current_version=? WHERE id=?",version,id);
            audit(id,actor,"LISTED",Map.of("version",version));
        } else {
            jdbc.update("UPDATE templates SET listed=FALSE WHERE id=?",id);
            audit(id,actor,"UNLISTED",Map.of());
        }
        return get(id);
    }

    public List<AuditEvent> audit(String id){ requireTemplate(id); return jdbc.query("SELECT id,action,CAST(details_json AS CHAR) details_json,created_at FROM template_audit_log WHERE template_id=? ORDER BY created_at DESC",
            (rs,row)->new AuditEvent(UUID.fromString(rs.getString("id")),rs.getString("action"),read(rs.getString("details_json")),instant(rs,"created_at")),id); }

    private void requireTemplate(String id){ Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM templates WHERE id=?",Integer.class,id); if(count==null||count==0)throw new ApiException(HttpStatus.NOT_FOUND,"TEMPLATE_NOT_FOUND","模板不存在"); }
    private boolean published(String id,int version){ Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM template_versions WHERE template_id=? AND version=? AND status='PUBLISHED'",Integer.class,id,version); return count!=null&&count>0; }
    private void audit(String id,UUID actor,String action,Object details){ jdbc.update("INSERT INTO template_audit_log(id,template_id,actor_user_id,action,details_json,created_at) VALUES(?,?,?,?,CAST(? AS JSON),?)",UUID.randomUUID().toString(),id,actor.toString(),action,write(details),Timestamp.from(clock.instant())); }
    private String validateName(String value){ String name=value==null?"":value.strip(); int length=name.codePointCount(0,name.length()); if(length<2||length>40)throw bad("TEMPLATE_NAME_INVALID","趣味展示名须为 2–40 个可见字符"); if(name.matches("(?i)^(template[-_ ]?\\d+|模板\\s*\\d+|通用模板|商务模板|简约模板)$"))throw bad("TEMPLATE_NAME_PLACEHOLDER","展示名不能使用内部 ID、纯序号或类别占位名"); return name; }
    private ApiException bad(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("invalid json",e);}}
    private JsonNode read(String value){if(value==null)return null;try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException("invalid template json",e);}}
    private TemplateDraft draft(ResultSet rs)throws SQLException{return new TemplateDraft(rs.getString("id"),rs.getString("display_name"),read(rs.getString("tags")),rs.getBoolean("listed"),nullableInt(rs,"current_version"),nullableInt(rs,"base_version"),rs.getString("schema_version"),read(rs.getString("config_json")),read(rs.getString("component_versions")),read(rs.getString("asset_manifest")),nullableLong(rs,"revision"),instant(rs,"updated_at"));}
    private static Integer nullableInt(ResultSet rs,String c)throws SQLException{int v=rs.getInt(c);return rs.wasNull()?null:v;}
    private static Long nullableLong(ResultSet rs,String c)throws SQLException{long v=rs.getLong(c);return rs.wasNull()?null:v;}
    private static Instant instant(ResultSet rs,String c)throws SQLException{Timestamp v=rs.getTimestamp(c);return v==null?null:v.toInstant();}

    public record CreateTemplate(String id,String displayName,List<String> tags,JsonNode initialConfig){}
    public record SaveDraft(long expectedRevision,String schemaVersion,JsonNode config,JsonNode componentVersions,JsonNode assetManifest){}
    public record TemplateSummary(String id,String displayName,JsonNode tags,boolean listed,Integer currentVersion,Integer baseVersion,Long draftRevision,Instant updatedAt){}
    public record TemplateDraft(String id,String displayName,JsonNode tags,boolean listed,Integer currentVersion,Integer baseVersion,String schemaVersion,JsonNode config,JsonNode componentVersions,JsonNode assetManifest,Long revision,Instant updatedAt){}
    public record PublishedVersion(String templateId,int version,Instant publishedAt){}
    public record AuditEvent(UUID id,String action,JsonNode details,Instant createdAt){}
}
