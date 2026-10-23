package com.resumint;

import static org.assertj.core.api.Assertions.assertThat;

import com.resumint.export.ExportCoordinator;
import com.resumint.export.ExportJobRepository;
import com.resumint.export.ExportSnapshot;
import com.resumint.export.ExportStatus;
import com.resumint.template.TemplateMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={"resumint.export.storage-root=target/mysql-it-storage","resumint.export.worker-enabled=false"})
@EnabledIfSystemProperty(named="resumint.mysql.it",matches="true")
class MySqlContractIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:33306/resumint?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        registry.add("spring.datasource.username",()->"resumint"); registry.add("spring.datasource.password",()->"resumint");
    }
    @Autowired TemplateMapper templates; @Autowired ExportCoordinator coordinator; @Autowired ExportJobRepository repository; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper objectMapper;
    @BeforeEach void clean(){jdbc.update("DELETE FROM export_jobs");jdbc.update("DELETE FROM resumes");jdbc.update("UPDATE templates SET listed=FALSE WHERE id='benchmark-template'");}
    @Test void migrationPublishesOnlyTheThreeFormalTemplates(){var rows=templates.findPublic();assertThat(rows).extracting(TemplateMapper.TemplateRecord::id).containsExactlyInAnyOrder("general-verdant","business-ledger","minimal-bareline");assertThat(rows).allSatisfy(row->{assertThat(row.version()).isEqualTo(1);assertThat(row.configJson()).contains("editableParameters");});}
    @Test void mysqlQueuePersistsLeaseIdempotencyAndGuestMetadataWithoutDrafts() throws Exception {
        var document=objectMapper.readTree("""
            {"schemaVersion":"1.0","documentId":"mysql-contract","template":{"id":"benchmark-template","version":1},"locale":"zh-CN","profile":{"fullName":"游客","headline":"测试","email":"guest@example.com"},"layout":{"kind":"single","page":{"size":"A4","marginMm":16},"theme":{"fontFamily":"Arial","accentColor":"#202221"},"decorations":[]},"sections":[{"id":"test-section","type":"custom","title":"测试","column":"main","visible":true,"items":[{"id":"test-item","title":"MySQL 队列"}]}]}
            """);
        var submitted=coordinator.submit("mysql-idempotency-key",new ExportSnapshot(document));var repeated=coordinator.submit("mysql-idempotency-key",new ExportSnapshot(document));
        assertThat(repeated.job().id()).isEqualTo(submitted.job().id());assertThat(repository.findById(submitted.job().id())).isPresent();
        assertThat(repository.claimNext("mysql-worker",Instant.now(),Duration.ofSeconds(60))).get().extracting(job->job.status()).isEqualTo(ExportStatus.RUNNING);
        Integer resumes=jdbc.queryForObject("SELECT COUNT(*) FROM resumes",Integer.class);assertThat(resumes).isZero();
        String metadata=jdbc.queryForObject("SELECT CONCAT(idempotency_key,'|',credential_digest,'|',snapshot_ref) FROM export_jobs WHERE id=?",String.class,submitted.job().id().toString());assertThat(metadata).doesNotContain("MySQL 队列","guest@example.com");
    }
}
