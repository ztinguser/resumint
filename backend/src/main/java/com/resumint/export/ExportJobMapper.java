package com.resumint.export;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ExportJobMapper {
    @Insert("""
        INSERT INTO export_jobs (id,idempotency_key,credential_digest,snapshot_ref,status,created_at,updated_at,credential_expires_at)
        VALUES (#{id},#{idempotencyKey},#{credentialDigest},#{snapshotRef},#{status},#{createdAt},#{updatedAt},#{credentialExpiresAt})
        """) void insert(ExportJob job);
    @Select("SELECT * FROM export_jobs WHERE id=#{id}") ExportJob findById(UUID id);
    @Select("SELECT * FROM export_jobs WHERE idempotency_key=#{key}") ExportJob findByIdempotencyKey(String key);
    @Select("SELECT id FROM export_jobs WHERE status='QUEUED' OR (status='RUNNING' AND lease_until <= #{now}) ORDER BY created_at LIMIT 1") UUID findClaimCandidate(Instant now);
    @Update("""
        UPDATE export_jobs SET status='RUNNING',lease_owner=#{owner},lease_until=#{until},updated_at=#{now}
        WHERE id=#{id} AND (status='QUEUED' OR (status='RUNNING' AND lease_until <= #{now}))
        """) int claim(@Param("id") UUID id, @Param("owner") String owner, @Param("now") Instant now, @Param("until") Instant until);
    @Update("""
        UPDATE export_jobs SET status=IF(cancel_requested,'CANCELLED','SUCCEEDED'), lease_owner=NULL, lease_until=NULL,
          result_ref=IF(cancel_requested,NULL,#{resultRef}), result_expires_at=IF(cancel_requested,#{now},#{expiresAt}), updated_at=#{now}
        WHERE id=#{id} AND status='RUNNING' AND lease_owner=#{owner}
        """) int succeed(@Param("id") UUID id,@Param("owner") String owner,@Param("resultRef") String resultRef,@Param("now") Instant now,@Param("expiresAt") Instant expiresAt);
    @Update("""
        UPDATE export_jobs SET status=IF(cancel_requested,'CANCELLED','FAILED'),lease_owner=NULL,lease_until=NULL,
          error_code=#{errorCode},result_expires_at=#{now},updated_at=#{now}
        WHERE id=#{id} AND status='RUNNING' AND lease_owner=#{owner}
        """) int fail(@Param("id") UUID id,@Param("owner") String owner,@Param("errorCode") String errorCode,@Param("now") Instant now);
    @Update("""
        UPDATE export_jobs SET cancel_requested=TRUE,status=IF(status='QUEUED','CANCELLED',status),
          result_expires_at=IF(status='QUEUED',#{now},result_expires_at),updated_at=#{now} WHERE id=#{id}
        """) int cancel(@Param("id") UUID id,@Param("now") Instant now);
    @Update("""
        UPDATE export_jobs SET status='EXPIRED',result_expires_at=#{now},lease_owner=NULL,lease_until=NULL,updated_at=#{now}
        WHERE (status='SUCCEEDED' AND result_expires_at <= #{now})
           OR (status='QUEUED' AND credential_expires_at <= #{now})
           OR (status='RUNNING' AND credential_expires_at <= #{now} AND lease_until <= #{now})
        """) void expire(Instant now);
    @Select("SELECT * FROM export_jobs WHERE result_expires_at <= #{now} AND (snapshot_ref IS NOT NULL OR result_ref IS NOT NULL)") List<ExportJob> cleanupCandidates(Instant now);
    @Update("UPDATE export_jobs SET snapshot_ref=NULL,result_ref=NULL,updated_at=#{now} WHERE id=#{id}") void clearRefs(@Param("id") UUID id,@Param("now") Instant now);
    @Select("SELECT COUNT(*) FROM export_jobs WHERE status IN ('QUEUED','RUNNING')") long activeCount();
}
