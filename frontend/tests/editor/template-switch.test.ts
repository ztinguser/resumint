import {describe,expect,it} from "vitest";
import {applyTemplateToDocument} from "../../src/features/editor/template-switch";
import {createBenchmarkDocument} from "../../src/resume/templates/benchmark";

describe("template switching",()=>{
  it("preserves content and stable custom ids while changing only template presentation",()=>{
    const source=createBenchmarkDocument();
    source.sections[0].items[0].fields=[{id:"custom-proof",label:"证明",type:"short-text",value:"必须保留"}];
    const switched=applyTemplateToDocument(source,{id:"business-ledger",name:"灰度之间",tags:[],version:2,config:{visualKey:"centered"}});
    expect(switched.template).toEqual({id:"business-ledger",version:2});
    expect(switched.sections).toEqual(source.sections);
    expect(switched.profile).toEqual(source.profile);
    expect(switched.layout.kind).toBe("two-column");
    expect(switched.layout.page.marginMm).toBe(14);
    expect(switched.layout.theme.accentColor).toBe("#242424");
    expect(source.template).toEqual({id:"benchmark-template",version:1});
  });
});
