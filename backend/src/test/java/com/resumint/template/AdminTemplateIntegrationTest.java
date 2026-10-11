package com.resumint.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resumint.auth.LocalSmsTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={"resumint.sms.provider=local-test","resumint.sms.cooldown=1ms","resumint.export.worker-enabled=false"})
@AutoConfigureMockMvc
@Import(LocalSmsTestConfiguration.class)
@EnabledIfSystemProperty(named="resumint.mysql.it",matches="true")
class AdminTemplateIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:33306/resumint?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        registry.add("spring.datasource.username",()->"resumint");registry.add("spring.datasource.password",()->"resumint");
    }
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;

    @BeforeEach void clean(){
        jdbc.update("DELETE FROM template_favorites");jdbc.update("DELETE FROM resumes");jdbc.update("DELETE FROM template_audit_log");jdbc.update("DELETE FROM template_drafts");
        jdbc.update("DELETE FROM template_versions WHERE template_id<>'benchmark-template'");jdbc.update("DELETE FROM templates WHERE id<>'benchmark-template'");
        jdbc.update("DELETE FROM trusted_devices");jdbc.update("DELETE FROM auth_sessions");jdbc.update("DELETE FROM sms_verification_attempts");jdbc.update("DELETE FROM users");
        jdbc.update("UPDATE templates SET listed=TRUE,current_version=1,display_name='排版基准模板' WHERE id='benchmark-template'");
    }

    @Test void adminOnlyWorkflowKeepsPublishedVersionsStableAndOldVersionReadableAfterUnlisting() throws Exception {
        Session regular=register("13500135101");
        mvc.perform(post("/api/admin/templates").cookie(regular.session,regular.csrf).header("X-XSRF-TOKEN",regular.csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(createBody("远山青"))).andExpect(status().isForbidden());

        Session admin=register("13500135102");
        UUID adminId=jdbc.queryForObject("SELECT id FROM users WHERE phone_e164='+8613500135102'",(rs,row)->UUID.fromString(rs.getString(1)));
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?",adminId.toString());
        mvc.perform(post("/api/admin/templates").cookie(admin.session,admin.csrf).header("X-XSRF-TOKEN",admin.csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(createBody("远山青")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("general-verdant")).andExpect(jsonPath("$.revision").value(1));

        mvc.perform(put("/api/admin/templates/general-verdant/draft").cookie(admin.session,admin.csrf).header("X-XSRF-TOKEN",admin.csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(saveBody(1,"verdant")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));
        mvc.perform(post("/api/admin/templates/general-verdant/publish").cookie(admin.session,admin.csrf).header("X-XSRF-TOKEN",admin.csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/admin/templates/general-verdant/listing").cookie(admin.session,admin.csrf).header("X-XSRF-TOKEN",admin.csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"listed\":true,\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.listed").value(true));

        mvc.perform(put("/api/admin/templates/general-verdant/draft").cookie(admin.session,admin.csrf).header("X-XSRF-TOKEN",admin.csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(saveBody(3,"verdant-v2"))).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(4));
        mvc.perform(post("/api/admin/templates/general-verdant/publish").cookie(admin.session,admin.csrf).header("X-XSRF-TOKEN",admin.csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json,'$.visualKey')) FROM template_versions WHERE template_id='general-verdant' AND version=1",String.class)).isEqualTo("verdant");

        mvc.perform(put("/api/admin/templates/general-verdant/name").cookie(admin.session,admin.csrf).header("X-XSRF-TOKEN",admin.csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"青岚序\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("general-verdant")).andExpect(jsonPath("$.displayName").value("青岚序"));
        mvc.perform(put("/api/admin/templates/general-verdant/listing").cookie(admin.session,admin.csrf).header("X-XSRF-TOKEN",admin.csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"listed\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.listed").value(false));
        mvc.perform(get("/api/templates/general-verdant")).andExpect(status().isNotFound());
        mvc.perform(get("/api/templates/general-verdant/versions/1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("青岚序")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/admin/templates/general-verdant/audit").cookie(admin.session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("UNLISTED"));
    }

    @Test void adminRoleDoesNotGrantAccessToAnotherUsersResume() throws Exception {
        Session owner=register("13500135111");Session admin=register("13500135112");
        jdbc.update("UPDATE users SET role='ADMIN' WHERE phone_e164='+8613500135112'");
        MvcResult created=mvc.perform(post("/api/resumes").cookie(owner.session,owner.csrf).header("X-XSRF-TOKEN",owner.csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"私有简历\",\"templateId\":\"benchmark-template\",\"templateVersion\":1,\"schemaVersion\":\"1.0\",\"document\":"+DOCUMENT+"}"))
                .andExpect(status().isCreated()).andReturn();
        String id=json.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/resumes/"+id).cookie(admin.session)).andExpect(status().isNotFound());
    }

    private Session register(String phone)throws Exception{
        MvcResult sent=mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\""+phone+"\",\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isOk()).andReturn();
        String attempt=json.readTree(sent.getResponse().getContentAsString()).get("attemptId").asText();
        MvcResult login=mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON).content("{\"attemptId\":\""+attempt+"\",\"phone\":\""+phone+"\",\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session(cookie(login,"resumint_session"),cookie(login,"XSRF-TOKEN"));
    }
    private String createBody(String name){return "{\"id\":\"general-verdant\",\"displayName\":\""+name+"\",\"category\":\"经典\",\"tags\":[\"通用\"],\"initialConfig\":{}}";}
    private String saveBody(long revision,String visual){return "{\"expectedRevision\":"+revision+",\"schemaVersion\":\"1.0\",\"config\":{\"visualKey\":\""+visual+"\",\"layoutTree\":{\"children\":[]},\"componentCatalog\":[{\"kind\":\"header\"}],\"bindings\":[{\"kind\":\"custom-fields\",\"target\":\"sections.*.items.*.fields\",\"emptyBehavior\":\"omit\"}],\"fallback\":{\"unknownModule\":\"通用模块\",\"unknownField\":\"自定义字段\"}},\"componentVersions\":{\"header\":1},\"assetManifest\":[]}";}
    private static Cookie cookie(MvcResult result,String name){return Arrays.stream(result.getResponse().getCookies()).filter(value->name.equals(value.getName())).findFirst().orElseThrow();}
    private record Session(Cookie session,Cookie csrf){}
    private static final String DOCUMENT="""
        {"schemaVersion":"1.0","documentId":"private-resume","template":{"id":"benchmark-template","version":1},"locale":"zh-CN","profile":{"fullName":"私有用户","headline":"测试","email":"private@example.com"},"layout":{"kind":"single","page":{"size":"A4","marginMm":16},"theme":{"fontFamily":"Arial","accentColor":"#202221"},"decorations":[]},"sections":[{"id":"section","type":"custom","title":"隐私","column":"main","visible":true,"items":[{"id":"item","title":"不得越权读取"}]}]}
        """;
}
