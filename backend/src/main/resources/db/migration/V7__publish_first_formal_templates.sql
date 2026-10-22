INSERT INTO templates (id, display_name, tags, listed, current_version)
VALUES
    ('general-verdant', '白昼来信', JSON_ARRAY('正式模板', '结构清晰', '直接编辑'), TRUE, 1),
    ('business-ledger', '灰度之间', JSON_ARRAY('正式模板', '双栏布局', '直接编辑'), TRUE, 1),
    ('minimal-bareline', '负空间', JSON_ARRAY('正式模板', '高留白', '直接编辑'), TRUE, 1)
ON DUPLICATE KEY UPDATE
    display_name=VALUES(display_name),
    tags=VALUES(tags),
    listed=TRUE,
    current_version=COALESCE(current_version, VALUES(current_version));

INSERT INTO template_versions
    (template_id, version, schema_version, config_json, component_versions, asset_manifest, status, published_at)
VALUES
    ('general-verdant', 1, '1.0',
     JSON_OBJECT(
       'visualKey', 'verdant',
       'layoutTree', JSON_OBJECT('type', 'page', 'children', JSON_ARRAY(
         JSON_OBJECT('id', 'header', 'kind', 'header', 'label', '姓名与联系信息', 'locked', TRUE),
         JSON_OBJECT('id', 'content', 'kind', 'section-list', 'label', '主内容', 'locked', FALSE),
         JSON_OBJECT('id', 'fallback', 'kind', 'custom-fallback', 'label', '自定义内容兜底', 'locked', TRUE)
       )),
       'componentCatalog', JSON_ARRAY(
         JSON_OBJECT('kind', 'header', 'label', '页首', 'allowed', TRUE),
         JSON_OBJECT('kind', 'section-list', 'label', '经历列表', 'allowed', TRUE),
         JSON_OBJECT('kind', 'divider', 'label', '细分隔线', 'allowed', TRUE),
         JSON_OBJECT('kind', 'custom-fallback', 'label', '自定义模块兜底', 'allowed', TRUE)
       ),
       'bindings', JSON_ARRAY(
         JSON_OBJECT('kind', 'profile', 'target', 'profile', 'emptyBehavior', 'omit-empty-value'),
         JSON_OBJECT('kind', 'repeating-items', 'target', 'sections.*.items', 'emptyBehavior', 'omit-empty-item'),
         JSON_OBJECT('kind', 'optional-section', 'target', 'sections.*', 'emptyBehavior', 'omit-section-keep-data'),
         JSON_OBJECT('kind', 'custom-fields', 'target', 'sections.*.items.*.fields', 'emptyBehavior', 'render-known-value'),
         JSON_OBJECT('kind', 'custom-sections', 'target', 'sections[type=custom]', 'emptyBehavior', 'generic-section-fallback')
       ),
       'editableParameters', JSON_OBJECT(
         'fontSizePt', JSON_OBJECT('type', 'number', 'min', 8, 'max', 11, 'step', 0.5, 'default', 8.5, 'locked', FALSE),
         'lineHeight', JSON_OBJECT('type', 'number', 'min', 1.3, 'max', 1.8, 'step', 0.05, 'default', 1.48, 'locked', FALSE),
         'marginMm', JSON_OBJECT('type', 'number', 'min', 12, 'max', 24, 'step', 1, 'default', 16, 'locked', FALSE),
         'accentColor', JSON_OBJECT('type', 'color', 'default', '#244b45', 'locked', FALSE),
         'pageSize', JSON_OBJECT('type', 'enum', 'values', JSON_ARRAY('A4'), 'default', 'A4', 'locked', TRUE)
       ),
       'fallback', JSON_OBJECT('unknownModule', '通用内容模块', 'unknownField', '自定义字段'),
       'continuation', JSON_OBJECT('kind', 'single-flow', 'repeatHeader', 'compact')
     ),
     JSON_OBJECT('resume-page', 1, 'header', 1, 'section-list', 1, 'portrait', 1, 'divider', 1, 'custom-fallback', 1),
     JSON_ARRAY(), 'PUBLISHED', CURRENT_TIMESTAMP(6)),
    ('business-ledger', 1, '1.0',
     JSON_OBJECT(
       'visualKey', 'centered',
       'layoutTree', JSON_OBJECT('type', 'page', 'children', JSON_ARRAY(
         JSON_OBJECT('id', 'header', 'kind', 'header', 'label', '居中肖像与姓名', 'locked', TRUE),
         JSON_OBJECT('id', 'columns', 'kind', 'section-list', 'label', '双栏内容', 'locked', FALSE, 'children', JSON_ARRAY(
           JSON_OBJECT('id', 'side', 'kind', 'skill-list', 'label', '信息与技能', 'locked', FALSE),
           JSON_OBJECT('id', 'main', 'kind', 'section-list', 'label', '教育与经历', 'locked', FALSE)
         )),
         JSON_OBJECT('id', 'fallback', 'kind', 'custom-fallback', 'label', '未知字段兜底', 'locked', TRUE)
       )),
       'componentCatalog', JSON_ARRAY(
         JSON_OBJECT('kind', 'header', 'label', '居中肖像页首', 'allowed', TRUE),
         JSON_OBJECT('kind', 'section-list', 'label', '经历列表', 'allowed', TRUE),
         JSON_OBJECT('kind', 'skill-list', 'label', '技能列表', 'allowed', TRUE),
         JSON_OBJECT('kind', 'custom-fallback', 'label', '自定义模块兜底', 'allowed', TRUE)
       ),
       'bindings', JSON_ARRAY(
         JSON_OBJECT('kind', 'profile', 'target', 'profile', 'emptyBehavior', 'omit-empty-value'),
         JSON_OBJECT('kind', 'repeating-items', 'target', 'sections.*.items', 'emptyBehavior', 'omit-empty-item'),
         JSON_OBJECT('kind', 'optional-section', 'target', 'sections.*', 'emptyBehavior', 'omit-section-keep-data'),
         JSON_OBJECT('kind', 'custom-fields', 'target', 'sections.*.items.*.fields', 'emptyBehavior', 'render-known-value'),
         JSON_OBJECT('kind', 'custom-sections', 'target', 'sections[type=custom]', 'emptyBehavior', 'generic-section-fallback')
       ),
       'editableParameters', JSON_OBJECT(
         'fontSizePt', JSON_OBJECT('type', 'number', 'min', 8, 'max', 11, 'step', 0.5, 'default', 8.5, 'locked', FALSE),
         'lineHeight', JSON_OBJECT('type', 'number', 'min', 1.3, 'max', 1.8, 'step', 0.05, 'default', 1.48, 'locked', FALSE),
         'marginMm', JSON_OBJECT('type', 'number', 'min', 12, 'max', 24, 'step', 1, 'default', 16, 'locked', FALSE),
         'accentColor', JSON_OBJECT('type', 'color', 'default', '#242424', 'locked', FALSE),
         'pageSize', JSON_OBJECT('type', 'enum', 'values', JSON_ARRAY('A4'), 'default', 'A4', 'locked', TRUE)
       ),
       'fallback', JSON_OBJECT('unknownModule', '通用内容模块', 'unknownField', '自定义字段'),
       'continuation', JSON_OBJECT('kind', 'explicit-two-column', 'side', 'side-flow', 'main', 'main-flow')
     ),
     JSON_OBJECT('resume-page', 1, 'header', 1, 'section-list', 1, 'portrait', 1, 'divider', 1, 'custom-fallback', 1),
     JSON_ARRAY(), 'PUBLISHED', CURRENT_TIMESTAMP(6)),
    ('minimal-bareline', 1, '1.0',
     JSON_OBJECT(
       'visualKey', 'bareline',
       'layoutTree', JSON_OBJECT('type', 'page', 'children', JSON_ARRAY(
         JSON_OBJECT('id', 'header', 'kind', 'header', 'label', '极简页首', 'locked', TRUE),
         JSON_OBJECT('id', 'content', 'kind', 'section-list', 'label', '纵向内容流', 'locked', FALSE),
         JSON_OBJECT('id', 'fallback', 'kind', 'custom-fallback', 'label', '自定义内容兜底', 'locked', TRUE)
       )),
       'componentCatalog', JSON_ARRAY(
         JSON_OBJECT('kind', 'header', 'label', '文字页首', 'allowed', TRUE),
         JSON_OBJECT('kind', 'section-list', 'label', '内容列表', 'allowed', TRUE),
         JSON_OBJECT('kind', 'divider', 'label', '发丝线', 'allowed', TRUE),
         JSON_OBJECT('kind', 'custom-fallback', 'label', '自定义模块兜底', 'allowed', TRUE)
       ),
       'bindings', JSON_ARRAY(
         JSON_OBJECT('kind', 'profile', 'target', 'profile', 'emptyBehavior', 'omit-empty-value'),
         JSON_OBJECT('kind', 'repeating-items', 'target', 'sections.*.items', 'emptyBehavior', 'omit-empty-item'),
         JSON_OBJECT('kind', 'optional-section', 'target', 'sections.*', 'emptyBehavior', 'omit-section-keep-data'),
         JSON_OBJECT('kind', 'custom-fields', 'target', 'sections.*.items.*.fields', 'emptyBehavior', 'render-known-value'),
         JSON_OBJECT('kind', 'custom-sections', 'target', 'sections[type=custom]', 'emptyBehavior', 'generic-section-fallback')
       ),
       'editableParameters', JSON_OBJECT(
         'fontSizePt', JSON_OBJECT('type', 'number', 'min', 8, 'max', 11, 'step', 0.5, 'default', 8.5, 'locked', FALSE),
         'lineHeight', JSON_OBJECT('type', 'number', 'min', 1.3, 'max', 1.8, 'step', 0.05, 'default', 1.48, 'locked', FALSE),
         'marginMm', JSON_OBJECT('type', 'number', 'min', 16, 'max', 28, 'step', 1, 'default', 20, 'locked', FALSE),
         'accentColor', JSON_OBJECT('type', 'color', 'default', '#202020', 'locked', TRUE),
         'pageSize', JSON_OBJECT('type', 'enum', 'values', JSON_ARRAY('A4'), 'default', 'A4', 'locked', TRUE)
       ),
       'fallback', JSON_OBJECT('unknownModule', '通用内容模块', 'unknownField', '自定义字段'),
       'continuation', JSON_OBJECT('kind', 'single-flow', 'repeatHeader', 'name-only')
     ),
     JSON_OBJECT('resume-page', 1, 'header', 1, 'section-list', 1, 'portrait', 1, 'divider', 1, 'custom-fallback', 1),
     JSON_ARRAY(), 'PUBLISHED', CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE template_id=VALUES(template_id);
