package com.resumint.template;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TemplateConfigValidatorTest {
    private final ObjectMapper json=new ObjectMapper();
    private final TemplateConfigValidator validator=new TemplateConfigValidator();

    @Test void acceptsControlledCatalogFallbackBindingsAndContentAddressedAssets() throws Exception {
        var config=json.readTree("""
            {"layoutTree":{"children":[]},"componentCatalog":[{"kind":"header"}],
             "bindings":[{"kind":"custom-fields","target":"sections.*.items.*.fields","emptyBehavior":"omit"}],
             "fallback":{"unknownModule":"通用模块","unknownField":"自定义字段"}}
            """);
        var assets=json.readTree("""
            [{"sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "path":"/templates/assets/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.woff2",
              "license":"SIL Open Font License 1.1"}]
            """);
        assertThat(validator.validate(config,json.readTree("{\"header\":1}"),assets)).isEmpty();
    }

    @Test void rejectsScriptsUnknownComponentsAndMutableAssetPaths() throws Exception {
        var config=json.readTree("""
            {"layoutTree":{"children":[]},"componentCatalog":[{"kind":"raw-html","script":"alert(1)"}],
             "bindings":[{"kind":"magic","target":"","emptyBehavior":"omit"}],
             "fallback":{"unknownModule":"通用模块"}}
            """);
        var errors=validator.validate(config,json.readTree("{}"),json.readTree("[{\"sha256\":\"bad\",\"path\":\"/latest/font.woff2\"}]"));
        assertThat(errors).anyMatch(value->value.contains("不支持的组件类型"))
                .anyMatch(value->value.contains("任意 HTML 或脚本"))
                .anyMatch(value->value.contains("内容摘要命名"))
                .anyMatch(value->value.contains("授权信息"));
    }
}
