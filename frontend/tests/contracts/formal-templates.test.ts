import {describe,expect,it} from "vitest";
import {paginateResume} from "../../src/resume/pagination";
import {createCandidateSample,formalTemplateCandidates} from "../../src/resume/templates/formal-drafts";

describe("M5 formal template drafts",()=>{
  it("uses three stable ids and unique non-placeholder display-name candidates",()=>{
    expect(formalTemplateCandidates.map(item=>item.stableId)).toEqual(["general-verdant","business-ledger","minimal-bareline"]);
    const names=formalTemplateCandidates.map(item=>item.recommendedName);
    expect(new Set(names).size).toBe(3);
    expect(names.every(name=>name.length>=2&&!/^(通用|商务|简约)模板$|待命名|设计预览/.test(name))).toBe(true);
  });

  it.each(formalTemplateCandidates)("$recommendedName candidate declares controlled components and fallback bindings",candidate=>{
    const config=candidate.config as {componentCatalog:Array<{kind:string}>;bindings:Array<{kind:string}>;fallback:Record<string,string>};
    expect(config.componentCatalog.length).toBeGreaterThan(2);
    expect(config.bindings.map(binding=>binding.kind)).toEqual(expect.arrayContaining(["repeating-items","custom-fields","custom-sections"]));
    expect(config.fallback).toEqual({unknownModule:"通用内容模块",unknownField:"自定义字段"});
    expect(candidate.assetManifest).toEqual([]);
  });

  it.each(formalTemplateCandidates)("$recommendedName preserves custom fields and paginates long content",candidate=>{
    const fallback=createCandidateSample(candidate,"fallback");
    const fallbackText=paginateResume(fallback).flatMap(page=>[...page.main,...page.side]).map(block=>block.text).join(" ");
    expect(fallbackText).toContain("研究方法：访谈 · 可用性测试");
    expect(fallbackText).toContain("关键结果：完成跨领域样本验证");
    expect(paginateResume(createCandidateSample(candidate,"long")).length).toBeGreaterThan(1);
  });

  it("does not expose subjective template classifications",()=>{
    expect(formalTemplateCandidates.every(candidate=>!("direction" in candidate)&&!("category" in candidate))).toBe(true);
  });

  it("declares a closed asset plan for every publishable candidate",()=>{
    expect(formalTemplateCandidates.every(candidate=>candidate.assetPlan.length>20)).toBe(true);
    expect(formalTemplateCandidates.every(candidate=>!candidate.assetPlan.includes("确认授权")&&!candidate.assetPlan.includes("再发布"))).toBe(true);
  });
});
