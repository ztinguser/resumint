import { describe, expect, it } from "vitest";
import { createGeneralDocument, GENERAL_AVATAR_PATH, GENERAL_TEMPLATE_ID } from "../../src/resume/templates/general";
import { validateResumeDocument } from "../../src/resume/validation";

describe("通用模板候选稿", () => {
  it("使用稳定模板 ID、中文标题和内容寻址示例素材", () => {
    const document = createGeneralDocument();
    expect(document.template.id).toBe(GENERAL_TEMPLATE_ID);
    expect(document.sections.map(section => section.title)).toEqual(["个人简介", "教育经历", "工作经历", "个人总结"]);
    expect(document.profile.portrait?.src).toBe(GENERAL_AVATAR_PATH);
    expect(GENERAL_AVATAR_PATH).toMatch(/general-avatar-[a-f0-9]{16}\.png$/);
  });

  it("编辑页实例内容均为中文结构稿", () => {
    const document = createGeneralDocument();
    expect(document.profile.fullName).toBe("林清远");
    expect(document.sections.flatMap(section => section.items).every(item => item.summary)).toBe(true);
    expect(document.sections.find(section => section.type === "experience")?.items).toHaveLength(2);
    expect(validateResumeDocument(document)).toMatchObject({ valid: true, errors: [] });
  });
});
