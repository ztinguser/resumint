import { describe, expect, test } from "vitest";
import { createGeneralDocument } from "../../src/resume/templates/general";
import { addContact, duplicateSectionAfter, elementStyleCss, insertItemAfter, moveById, removeById, themeVariables } from "../../src/features/editor/direct-canvas";
import { paginateGeneralResume } from "../../src/resume/general-pagination";

describe("direct resume canvas", () => {
  test("moves a section by stable id", () => {
    const document=createGeneralDocument();
    document.sections=moveById(document.sections,"general-education","general-experience");
    expect(document.sections.map(section=>section.id)).toEqual(["general-profile","general-experience","general-education","general-summary"]);
  });

  test("removes only the requested stable id", () => {
    const items=createGeneralDocument().sections.find(section=>section.id==="general-education")!.items;
    expect(removeById(items,"general-education-1").map(item=>item.id)).toEqual(["general-education-2"]);
  });

  test("inserts a copy immediately after an entry", () => {
    const document=createGeneralDocument();
    const section=document.sections.find(value=>value.id==="general-education")!;
    section.items=insertItemAfter(section.items,"general-education-1","new-education");
    expect(section.items.map(item=>item.id)).toEqual(["general-education-1","new-education","general-education-2"]);
  });

  test("adds open-ended contact data without replacing fixed profile fields", () => {
    const document=addContact(createGeneralDocument(),"contact-extra");
    expect(document.profile.contacts).toEqual([{id:"contact-extra",label:"联系方式",value:"双击填写"}]);
    expect(document.profile.email).toBe("qingyuan.lin@example.com");
  });

  test("duplicates the complete module after itself with independent stable ids", () => {
    const document=createGeneralDocument();
    const source=document.sections.find(section=>section.id==="general-experience")!;
    source.items[0].fields=[{id:"source-field",label:"地点",type:"short-text",value:"上海"}];
    source.items[0].richText=[{id:"source-block",type:"paragraph",spans:[{text:"完整内容"}]}];
    document.elementStyles={"section.general-experience.title":{color:"#123456"},"item.general-work-1.title":{fontSizePt:12}};
    let id=0;
    document.sections=duplicateSectionAfter(document.sections,"general-experience","section-copy",kind=>`${kind}-${++id}`,document.elementStyles);
    expect(document.sections.map(section=>section.id)).toEqual(["general-profile","general-education","general-experience","section-copy","general-summary"]);
    const copy=document.sections[3];
    expect(copy).toMatchObject({type:source.type,title:source.title,column:source.column,visible:source.visible});
    expect(copy.items.map(item=>({...item,id:undefined,fields:item.fields?.map(field=>({...field,id:undefined})),richText:item.richText?.map(block=>({...block,id:undefined}))}))).toEqual(source.items.map(item=>({...item,id:undefined,fields:item.fields?.map(field=>({...field,id:undefined})),richText:item.richText?.map(block=>({...block,id:undefined}))})));
    expect(copy.items.map(item=>item.id)).not.toEqual(source.items.map(item=>item.id));
    expect(copy.items[0].fields?.[0].id).not.toBe(source.items[0].fields?.[0].id);
    expect(copy.items[0].richText?.[0].id).not.toBe(source.items[0].richText?.[0].id);
    expect(document.elementStyles["section.section-copy.title"]).toEqual({color:"#123456"});
    expect(document.elementStyles["item.item-1.title"]).toEqual({fontSizePt:12});
  });

  test("maps a selected element style to inline canvas properties", () => {
    const document=createGeneralDocument();
    document.elementStyles={"item.general-education-1.title":{fontFamily:"SimSun",fontSizePt:12,color:"#91452f",lineHeight:1.4,spacingMm:2}};
    expect(elementStyleCss(document,"item.general-education-1.title")).toMatchObject({fontSize:"12pt",color:"#91452f",lineHeight:1.4,paddingBottom:"2mm"});
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
