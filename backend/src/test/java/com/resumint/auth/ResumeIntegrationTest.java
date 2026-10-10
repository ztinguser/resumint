package com.resumint.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={"resumint.sms.provider=fake","resumint.sms.cooldown=1s","resumint.export.worker-enabled=false"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="resumint.mysql.it",matches="true")
class ResumeIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:33306/resumint?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        registry.add("spring.datasource.username",()->"resumint");registry.add("spring.datasource.password",()->"resumint");
    }
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @BeforeEach void clean(){jdbc.update("DELETE FROM template_favorites");jdbc.update("DELETE FROM resumes");jdbc.update("DELETE FROM trusted_devices");jdbc.update("DELETE FROM auth_sessions");jdbc.update("DELETE FROM sms_verification_attempts");jdbc.update("DELETE FROM users");}

    @Test void manualDraftRevisionConflictAndFavoritesAreEnforced() throws Exception {
        MvcResult registration=register("13500135000");
        Cookie session=cookie(registration,AuthSessionFilter.SESSION_COOKIE);Cookie csrf=cookie(registration,AuthSessionFilter.CSRF_COOKIE);
        String create=json.writeValueAsString(Map.of("title","我的简历","templateId","benchmark-template","templateVersion",1,"schemaVersion","1.0","document",json.readTree(DOCUMENT)));
        MvcResult created=mvc.perform(post("/api/resumes").cookie(session,csrf).header("X-XSRF-TOKEN",csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.revision").value(1)).andReturn();
        String id=json.readTree(created.getResponse().getContentAsString()).get("id").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resumes",Integer.class)).isOne();

        String firstSave=json.writeValueAsString(Map.of("baseRevision",1,"title","服务器版本","document",json.readTree(DOCUMENT.replace("游客","版本 A"))));
        mvc.perform(put("/api/resumes/"+id).cookie(session,csrf).header("X-XSRF-TOKEN",csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(firstSave))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));
        String staleSave=json.writeValueAsString(Map.of("baseRevision",1,"title","本地冲突版本","document",json.readTree(DOCUMENT.replace("游客","版本 B"))));
        mvc.perform(put("/api/resumes/"+id).cookie(session,csrf).header("X-XSRF-TOKEN",csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(staleSave))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT")).andExpect(jsonPath("$.details.currentRevision").value(2));
        mvc.perform(get("/api/resumes/"+id).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("服务器版本"))
                .andExpect(jsonPath("$.document.profile.fullName").value("版本 A"));

        mvc.perform(patch("/api/resumes/"+id+"/title").cookie(session,csrf).header("X-XSRF-TOKEN",csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseRevision\":2,\"title\":\"重命名草稿\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("重命名草稿"))
                .andExpect(jsonPath("$.revision").value(3));

        mvc.perform(put("/api/templates/benchmark-template/favorite").cookie(session,csrf).header("X-XSRF-TOKEN",csrf.getValue())).andExpect(status().isOk());
        mvc.perform(put("/api/templates/benchmark-template/favorite").cookie(session,csrf).header("X-XSRF-TOKEN",csrf.getValue())).andExpect(status().isOk());
        mvc.perform(get("/api/templates/favorites").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("benchmark-template"));
        mvc.perform(delete("/api/templates/benchmark-template/favorite").cookie(session,csrf).header("X-XSRF-TOKEN",csrf.getValue())).andExpect(status().isOk());
        mvc.perform(delete("/api/resumes/"+id+"?baseRevision=3").cookie(session,csrf).header("X-XSRF-TOKEN",csrf.getValue()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resumes",Integer.class)).isZero();
    }

    private MvcResult register(String phone)throws Exception{
        MvcResult sent=mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\""+phone+"\",\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isOk()).andReturn();
        UUID attempt=UUID.fromString(json.readTree(sent.getResponse().getContentAsString()).get("attemptId").asText());
        return mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON).content("{\"attemptId\":\""+attempt+"\",\"phone\":\""+phone+"\",\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn();
    }
    private static Cookie cookie(MvcResult result,String name){return Arrays.stream(result.getResponse().getCookies()).filter(value->name.equals(value.getName())).findFirst().orElseThrow();}
    @TestConfiguration static class FakeSmsConfiguration{
        @Bean @Primary SmsProvider fakeSmsProvider(){return new SmsProvider(){
            public String name(){return "fake";}public SendResult send(String phone,String outId,String purpose){return new SendResult("fake-"+outId);}public boolean verify(String phone,String outId,String code){return "123456".equals(code);}
        };}
    }
    private static final String DOCUMENT="""
            {"schemaVersion":"1.0","documentId":"m4-contract","template":{"id":"benchmark-template","version":1},"locale":"zh-CN","profile":{"fullName":"游客","headline":"测试","email":"guest@example.com"},"layout":{"kind":"single","page":{"size":"A4","marginMm":16},"theme":{"fontFamily":"Arial","accentColor":"#202221"},"decorations":[]},"sections":[{"id":"section","type":"custom","title":"测试","column":"main","visible":true,"items":[{"id":"item","title":"手动保存"}]}]}
            """;
}
