import{describe,expect,it}from"vitest";
import{validateResumeDocument}from"../../src/resume/validation";
import{CENTERED_TEMPLATE_ID,CENTERED_TEMPLATE_NAME,createCenteredDocument}from"../../src/resume/templates/centered";
import{GENERAL_AVATAR_PATH}from"../../src/resume/templates/general";

describe("第二套居中双栏模板候选稿",()=>{
  it("保留稳定模板 ID，并在确认前使用待命名展示名",()=>{
    const document=createCenteredDocument();
    expect(CENTERED_TEMPLATE_ID).toBe("business-ledger");
    expect(CENTERED_TEMPLATE_NAME).toContain("待命名");
    expect(document.template).toEqual({id:CENTERED_TEMPLATE_ID,version:1});
    expect(document.layout.kind).toBe("two-column");
    expect(document.profile.portrait?.src).toBe(GENERAL_AVATAR_PATH);
  });

  it("提供中文实例、左右栏内容和可编辑联系方式",()=>{
    const document=createCenteredDocument();
    expect(document.profile.contacts?.map(contact=>contact.label)).toEqual(["电话","邮箱","所在地","个人主页"]);
    expect(document.sections.filter(section=>section.column==="side").map(section=>section.title)).toEqual(["个人简介","主要技能"]);
    expect(document.sections.filter(section=>section.column==="main").map(section=>section.title)).toEqual(["教育经历","工作经历"]);
    expect(validateResumeDocument(document)).toMatchObject({valid:true,errors:[]});
  });
});
