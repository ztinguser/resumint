import{describe,expect,it}from"vitest";
import{validateResumeDocument}from"../../src/resume/validation";
import{BARELINE_TEMPLATE_ID,BARELINE_TEMPLATE_NAME,createBarelineDocument}from"../../src/resume/templates/bareline";

describe("第三套高留白双栏模板候选稿",()=>{
  it("保留稳定模板 ID，并使用独立的正式展示名",()=>{
    const document=createBarelineDocument();
    expect(BARELINE_TEMPLATE_ID).toBe("minimal-bareline");
    expect(BARELINE_TEMPLATE_NAME).toBe("负空间");
    expect(document.template).toEqual({id:BARELINE_TEMPLATE_ID,version:1});
    expect(document.layout.kind).toBe("two-column");
    expect(document.profile.portrait).toBeUndefined();
  });

  it("提供中文实例、紧凑左右栏与多行经历内容",()=>{
    const document=createBarelineDocument();
    expect(document.profile.contacts?.map(contact=>contact.label)).toEqual(["个人主页","邮箱","电话"]);
    expect(document.sections.filter(section=>section.column==="side").map(section=>section.title)).toEqual(["专业技能","常用工具"]);
    expect(document.sections.filter(section=>section.column==="main").map(section=>section.title)).toEqual(["工作经历","教育经历"]);
    expect(document.sections.find(section=>section.id==="bareline-experience")?.items[0].summary).toContain("\n•");
    expect(validateResumeDocument(document)).toMatchObject({valid:true,errors:[]});
  });
});
