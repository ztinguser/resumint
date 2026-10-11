package com.resumint.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
final class TemplateConfigValidator {
    private static final Set<String> COMPONENTS = Set.of(
            "header", "profile", "portrait", "section-list", "skill-list", "divider", "shape", "custom-fallback");
    private static final Set<String> BINDINGS = Set.of(
            "profile", "single-field", "repeating-items", "optional-section", "custom-fields", "custom-sections");
    private static final Pattern DIGEST = Pattern.compile("^[a-f0-9]{64}$");

    List<String> validate(JsonNode config, JsonNode components, JsonNode assets) {
        List<String> errors = new ArrayList<>();
        if (!config.isObject()) errors.add("config 必须是对象");
        JsonNode layoutTree = config.path("layoutTree");
        if (!layoutTree.isObject() || !layoutTree.path("children").isArray()) errors.add("layoutTree.children 必须是数组");
        JsonNode bindings = config.path("bindings");
        if (!bindings.isArray() || bindings.isEmpty()) errors.add("bindings 至少包含一项字段绑定");
        else bindings.forEach(binding -> {
            String kind = binding.path("kind").asText();
            if (!BINDINGS.contains(kind)) errors.add("不支持的字段绑定: " + kind);
            if (binding.path("target").asText().isBlank()) errors.add("字段绑定缺少 target");
            if (!binding.has("emptyBehavior")) errors.add("字段绑定缺少 emptyBehavior");
        });
        JsonNode catalog = config.path("componentCatalog");
        if (!catalog.isArray() || catalog.isEmpty()) errors.add("componentCatalog 至少包含一个受控组件");
        else catalog.forEach(component -> {
            String kind = component.path("kind").asText();
            if (!COMPONENTS.contains(kind)) errors.add("不支持的组件类型: " + kind);
            if (component.has("html") || component.has("script") || component.has("javascript")) errors.add("组件不得包含任意 HTML 或脚本");
        });
        if (!config.path("fallback").path("unknownField").isTextual()
                || !config.path("fallback").path("unknownModule").isTextual()) errors.add("必须声明未知字段与自定义模块兜底");
        if (!components.isObject() || components.isEmpty()) errors.add("componentVersions 必须锁定组件版本");
        if (!assets.isArray()) errors.add("assetManifest 必须是数组");
        else assets.forEach(asset -> {
            String digest = asset.path("sha256").asText();
            String path = asset.path("path").asText();
            if (!DIGEST.matcher(digest).matches()) errors.add("素材 sha256 必须是 64 位小写十六进制");
            if (!path.startsWith("/templates/assets/" + digest + ".")) errors.add("素材路径必须使用内容摘要命名");
            if (asset.path("license").asText().isBlank()) errors.add("素材必须记录授权信息");
        });
        return errors;
    }
}
