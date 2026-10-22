UPDATE templates
SET listed = FALSE
WHERE id = 'benchmark-template';

DELETE FROM template_favorites
WHERE template_id = 'benchmark-template';
