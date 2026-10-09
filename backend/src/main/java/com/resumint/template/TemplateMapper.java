package com.resumint.template;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TemplateMapper {
    @Select("""
        SELECT t.id,t.name,t.category,CAST(t.tags AS CHAR) tags,t.current_version version,
          tv.schema_version,CAST(tv.config_json AS CHAR) config_json,tv.published_at
        FROM templates t JOIN template_versions tv ON tv.template_id=t.id AND tv.version=t.current_version
        WHERE t.listed=TRUE AND tv.status='PUBLISHED' ORDER BY t.created_at
        """) List<TemplateRecord> findPublic();
    @Select("""
        SELECT t.id,t.name,t.category,CAST(t.tags AS CHAR) tags,t.current_version version,
          tv.schema_version,CAST(tv.config_json AS CHAR) config_json,tv.published_at
        FROM templates t JOIN template_versions tv ON tv.template_id=t.id AND tv.version=t.current_version
        WHERE t.id=#{id} AND t.listed=TRUE AND tv.status='PUBLISHED'
        """) TemplateRecord findPublicById(@Param("id") String id);
    record TemplateRecord(String id,String name,String category,String tags,int version,String schemaVersion,String configJson,Instant publishedAt) { }
}
