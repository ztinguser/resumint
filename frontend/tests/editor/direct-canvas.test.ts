import { describe, expect, test } from "vitest";
import { createGeneralDocument } from "../../src/resume/templates/general";
import { addContact, insertItemAfter, moveById, themeVariables } from "../../src/features/editor/direct-canvas";
import { paginateGeneralResume } from "../../src/resume/general-pagination";

describe("direct resume canvas", () => {
  test("moves a section by stable id", () => {
    const document=createGeneralDocument();
    document.sections=moveById(document.sections,"general-education","general-experience");
    expect(document.sections.map(section=>section.id)).toEqual(["general-profile","general-experience","general-education","general-summary"]);
  });

  test("inserts a copy immediately after an entry", () => {
    const document=createGeneralDocument();
    const section=document.sections.find(value=>value.id==="general-education")!;
    section.items=insertItemAfter(section.items,"general-education-1","new-education");
    expect(section.items.map(item=>item.id)).toEqual(["general-education-1","new-education","general-education-2"]);
  });

  test("adds open-ended contact data without replacing fixed profile fields", () => {
    const document=addContact(createGeneralDocument(),"contact-extra");
    expect(document.profile.contacts).toEqual([{id:"contact-extra",label:"其他",value:"双击填写"}]);
    expect(document.profile.email).toBe("qingyuan.lin@example.com");
  });

  test("maps theme controls to live canvas variables", () => {
    const document=createGeneralDocument();
    document.layout.theme.accentColor="#c0392b";
    document.layout.theme.fontSizePt=10;
    expect(themeVariables(document)).toMatchObject({"--accent":"#c0392b","--body-font-size":"10pt"});
  });

  test("paginates long general-template content for export", () => {
    const document=createGeneralDocument();
    const work=document.sections.find(value=>value.id==="general-experience")!;
    work.items=Array.from({length:12},(_,index)=>({...work.items[index%2],id:`long-work-${index}`,summary:"跨团队推进复杂项目并持续验证业务结果。".repeat(8)}));
    expect(paginateGeneralResume(document).length).toBeGreaterThan(1);
  });

  test("keeps the initial general-template sample on one export page", () => {
    expect(paginateGeneralResume(createGeneralDocument())).toHaveLength(1);
  });
});
