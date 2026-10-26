package com.resumint.system;

import com.resumint.export.ExportStorage;
import com.resumint.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class OperationalStatusController {
    private final JdbcTemplate jdbc;
    private final ExportStorage storage;
    private final String token;
    private final int smsWarning;

    OperationalStatusController(JdbcTemplate jdbc, ExportStorage storage,
            @Value("${resumint.ops.token}") String token,
            @Value("${resumint.sms.daily-cost-warning-count}") int smsWarning) {
        this.jdbc=jdbc;this.storage=storage;this.token=token;this.smsWarning=smsWarning;
    }

    @GetMapping("/api/ops/status")
    Map<String,Object> status(@RequestHeader(value="X-Ops-Token",required=false) String supplied) {
        if (!matches(supplied)) throw new ApiException(HttpStatus.FORBIDDEN,"OPS_TOKEN_INVALID","运维凭据无效");
        Instant dayStart=Instant.now().truncatedTo(ChronoUnit.DAYS);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("status","ok");
        result.put("smsSentToday",count("SELECT COUNT(*) FROM sms_verification_attempts WHERE created_at>=? AND status IN ('SENT','VERIFIED','CONSUMED')",dayStart));
        result.put("smsFailedToday",count("SELECT COUNT(*) FROM sms_verification_attempts WHERE created_at>=? AND status='SEND_FAILED'",dayStart));
        result.put("smsDailyCostWarningCount",smsWarning);
        result.put("smsCostAlertedToday",count("SELECT COUNT(*) FROM operational_alerts WHERE alert_type='SMS_DAILY_COST' AND window_start=?",dayStart)>0);
        result.put("alertEmailsPending",count("SELECT COUNT(*) FROM operational_alerts WHERE notification_status IN ('PENDING','RETRY','SENDING')"));
        result.put("alertEmailsFailed",count("SELECT COUNT(*) FROM operational_alerts WHERE notification_status='FAILED'"));
        result.put("exportsQueued",count("SELECT COUNT(*) FROM export_jobs WHERE status='QUEUED'"));
        result.put("exportsRunning",count("SELECT COUNT(*) FROM export_jobs WHERE status='RUNNING'"));
        result.put("exportsFailedToday",count("SELECT COUNT(*) FROM export_jobs WHERE status='FAILED' AND updated_at>=?",dayStart));
        result.put("exportStorageUsableBytes",storage.usableSpace());
        result.put("checkedAt",Instant.now());
        return result;
    }

    private long count(String sql,Object... args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0:value;}
    private boolean matches(String supplied){
        if(supplied==null||token==null)return false;
        return MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8));
    }
}
