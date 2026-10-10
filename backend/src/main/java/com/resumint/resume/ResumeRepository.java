package com.resumint.resume;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ResumeRepository {
    private final JdbcTemplate jdbc;
    ResumeRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    List<ResumeRow> list(UUID userId) {
        return jdbc.query("SELECT id,user_id,title,template_id,template_version,schema_version,CAST(document_json AS CHAR) document_json,revision,created_at,updated_at FROM resumes WHERE user_id=? ORDER BY updated_at DESC",
                (rs,row)->map(rs),userId.toString());
    }
    Optional<ResumeRow> find(UUID userId, UUID id) {
        return jdbc.query("SELECT id,user_id,title,template_id,template_version,schema_version,CAST(document_json AS CHAR) document_json,revision,created_at,updated_at FROM resumes WHERE user_id=? AND id=?",
                (rs,row)->map(rs),userId.toString(),id.toString()).stream().findFirst();
    }
    void insert(ResumeRow row) {
        jdbc.update("INSERT INTO resumes(id,user_id,title,template_id,template_version,schema_version,document_json,revision,created_at,updated_at) VALUES(?,?,?,?,?,?,CAST(? AS JSON),?,?,?)",
                row.id().toString(),row.userId().toString(),row.title(),row.templateId(),row.templateVersion(),row.schemaVersion(),
                row.documentJson(),row.revision(),at(row.createdAt()),at(row.updatedAt()));
    }
    int update(UUID userId,UUID id,long baseRevision,String title,String documentJson,Instant now) {
        return jdbc.update("UPDATE resumes SET title=?,document_json=CAST(? AS JSON),revision=revision+1,updated_at=? WHERE user_id=? AND id=? AND revision=?",
                title,documentJson,at(now),userId.toString(),id.toString(),baseRevision);
    }
    int rename(UUID userId,UUID id,long baseRevision,String title,Instant now) {
        return jdbc.update("UPDATE resumes SET title=?,revision=revision+1,updated_at=? WHERE user_id=? AND id=? AND revision=?",
                title,at(now),userId.toString(),id.toString(),baseRevision);
    }
    int delete(UUID userId,UUID id,long baseRevision) {
        return jdbc.update("DELETE FROM resumes WHERE user_id=? AND id=? AND revision=?",userId.toString(),id.toString(),baseRevision);
    }
    boolean templateVersionExists(String templateId,int version) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM template_versions WHERE template_id=? AND version=? AND status='PUBLISHED'",Integer.class,templateId,version);
        return count!=null&&count>0;
    }
    private static ResumeRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ResumeRow(UUID.fromString(rs.getString("id")),UUID.fromString(rs.getString("user_id")),rs.getString("title"),
                rs.getString("template_id"),rs.getInt("template_version"),rs.getString("schema_version"),rs.getString("document_json"),
                rs.getLong("revision"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());
    }
    private static Timestamp at(Instant value){return Timestamp.from(value);}
    record ResumeRow(UUID id,UUID userId,String title,String templateId,int templateVersion,String schemaVersion,String documentJson,
                     long revision,Instant createdAt,Instant updatedAt){}
}
