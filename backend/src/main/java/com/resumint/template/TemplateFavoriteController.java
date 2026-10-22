package com.resumint.template;

import com.resumint.auth.AuthPrincipal;
import com.resumint.web.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
public class TemplateFavoriteController {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    TemplateFavoriteController(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}

    @GetMapping("/favorites")
    List<String> favorites(@AuthenticationPrincipal AuthPrincipal principal){
        return jdbc.query("""
                SELECT f.template_id
                FROM template_favorites f
                JOIN templates t ON t.id=f.template_id AND t.listed=TRUE
                WHERE f.user_id=?
                ORDER BY f.created_at DESC
                """,(rs,row)->rs.getString(1),principal.userId().toString());
    }

    @PutMapping("/{templateId}/favorite")
    @Transactional Map<String,Boolean> favorite(@AuthenticationPrincipal AuthPrincipal principal,@PathVariable String templateId){
        ensureTemplate(templateId);
        jdbc.update("INSERT INTO template_favorites(user_id,template_id,created_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE template_id=VALUES(template_id)",
                principal.userId().toString(),templateId,Timestamp.from(clock.instant()));
        return Map.of("favorite",true);
    }

    @DeleteMapping("/{templateId}/favorite")
    Map<String,Boolean> unfavorite(@AuthenticationPrincipal AuthPrincipal principal,@PathVariable String templateId){
        jdbc.update("DELETE FROM template_favorites WHERE user_id=? AND template_id=?",principal.userId().toString(),templateId);
        return Map.of("favorite",false);
    }

    private void ensureTemplate(String templateId){
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM templates WHERE id=? AND listed=TRUE",Integer.class,templateId);
        if(count==null||count==0)throw new ApiException(HttpStatus.NOT_FOUND,"TEMPLATE_NOT_FOUND","模板不存在或不可收藏");
    }
}
