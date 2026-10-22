import{describe,expect,it}from"vitest";
import{mergeTemplateCatalog,type TemplateCatalogItem}from"../../src/resume/templates/catalog";

describe("template catalog publication state",()=>{
  const previews:TemplateCatalogItem[]=[
    {id:"general-verdant",name:"待确认 01",tags:["直接编辑"],version:1},
    {id:"business-ledger",name:"待确认 02",tags:["双栏布局"],version:1},
  ];

  it("replaces a local preview with the backend-published name and version",()=>{
    const result=mergeTemplateCatalog(previews,[{id:"general-verdant",name:"白昼来信",tags:["正式模板"],version:3}]);
    expect(result.templates).toEqual([
      {id:"general-verdant",name:"白昼来信",tags:["正式模板"],version:3},
      previews[1],
    ]);
    expect([...result.unpublishedPreviewIds]).toEqual(["business-ledger"]);
  });

  it("retains published templates that have no bundled preview",()=>{
    const remote={id:"custom-published",name:"远端模板",tags:[],version:1};
    expect(mergeTemplateCatalog(previews,[remote]).templates.at(-1)).toEqual(remote);
  });
});
