import type {ResumeDocument} from "../model";
import {createBenchmarkDocument} from "./benchmark";

export type FormalTemplateCandidate={
  stableId:"general-verdant"|"business-ledger"|"minimal-bareline";
  recommendedName:string;
  alternateNames:string[];
  rationale:string;
  assetPlan:string;
  config:Record<string,unknown>;
  componentVersions:Record<string,number>;
  assetManifest:Array<{sha256:string;path:string;mediaType:string;license:string}>;
};

const bindings=[
  {kind:"profile",target:"profile",emptyBehavior:"omit-empty-value"},
  {kind:"repeating-items",target:"sections.*.items",emptyBehavior:"omit-empty-item"},
  {kind:"optional-section",target:"sections.*",emptyBehavior:"omit-section-keep-data"},
  {kind:"custom-fields",target:"sections.*.items.*.fields",emptyBehavior:"render-known-value"},
  {kind:"custom-sections",target:"sections[type=custom]",emptyBehavior:"generic-section-fallback"},
];
const editableParameters={
  fontSizePt:{type:"number",min:8,max:11,step:.5,default:8.5,locked:false},
  lineHeight:{type:"number",min:1.3,max:1.8,step:.05,default:1.48,locked:false},
  marginMm:{type:"number",min:12,max:24,step:1,default:16,locked:false},
  accentColor:{type:"color",default:"#244b45",locked:false},
  pageSize:{type:"enum",values:["A4"],default:"A4",locked:true},
};
const fallback={unknownModule:"通用内容模块",unknownField:"自定义字段"};
const componentVersions={"resume-page":1,"header":1,"section-list":1,"portrait":1,"divider":1,"custom-fallback":1};

export const formalTemplateCandidates:FormalTemplateCandidate[]=[
  {
    stableId:"general-verdant",recommendedName:"白昼来信",alternateNames:["远山青","青岚序"],
    rationale:"稳重的松绿色细线与清晰单栏，兼容应届生和有经验求职者，信息密度中等。",
    assetPlan:"模板结构仅使用 CSS 几何分隔线和系统字体栈，不依赖模板级照片、外部图标、远程资源或需单独分发的字体文件。用户简历头像属于内容数据，不作为模板资产发布。",
    config:{visualKey:"verdant",layoutTree:{type:"page",children:[{id:"header",kind:"header",label:"姓名与联系信息",locked:true},{id:"content",kind:"section-list",label:"主内容",locked:false},{id:"fallback",kind:"custom-fallback",label:"自定义内容兜底",locked:true}]},componentCatalog:[{kind:"header",label:"页首",allowed:true},{kind:"section-list",label:"经历列表",allowed:true},{kind:"divider",label:"细分隔线",allowed:true},{kind:"custom-fallback",label:"自定义模块兜底",allowed:true}],bindings,editableParameters,fallback,continuation:{kind:"single-flow",repeatHeader:"compact"}},
    componentVersions,assetManifest:[],
  },
  {
    stableId:"business-ledger",recommendedName:"灰度之间",alternateNames:["圆心录","中轴札记"],
    rationale:"居中圆形肖像与高留白建立第一视觉焦点，下方黑灰双栏把个人信息和能力放在左侧、教育与经历放在右侧。",
    assetPlan:"模板结构仅使用 CSS 排版和系统字体栈，不依赖外部图标、远程资源或需单独分发的字体文件。预览中的示例头像属于样本内容，用户可替换，不作为模板资产发布。",
    config:{visualKey:"centered",layoutTree:{type:"page",children:[{id:"header",kind:"header",label:"居中肖像与姓名",locked:true},{id:"columns",kind:"section-list",label:"双栏内容",locked:false,children:[{id:"side",kind:"skill-list",label:"信息与技能",locked:false},{id:"main",kind:"section-list",label:"教育与经历",locked:false}]},{id:"fallback",kind:"custom-fallback",label:"未知字段兜底",locked:true}]},componentCatalog:[{kind:"header",label:"居中肖像页首",allowed:true},{kind:"section-list",label:"经历列表",allowed:true},{kind:"skill-list",label:"技能列表",allowed:true},{kind:"custom-fallback",label:"自定义模块兜底",allowed:true}],bindings,editableParameters:{...editableParameters,accentColor:{type:"color",default:"#242424",locked:false}},fallback,continuation:{kind:"explicit-two-column",side:"side-flow",main:"main-flow"}},
    componentVersions,assetManifest:[],
  },
  {
    stableId:"minimal-bareline",recommendedName:"负空间",alternateNames:["留白信笺","静默刻度"],
    rationale:"黑白高留白、窄字阶和极细规则线，让内容成为视觉中心，适合文本克制的投递场景。",
    assetPlan:"仅使用 CSS 规则线、背景渐变与系统字体栈，不含模板级图片、图标、远程资源或需单独分发的字体文件。",
    config:{visualKey:"bareline",layoutTree:{type:"page",children:[{id:"header",kind:"header",label:"极简页首",locked:true},{id:"content",kind:"section-list",label:"纵向内容流",locked:false},{id:"fallback",kind:"custom-fallback",label:"自定义内容兜底",locked:true}]},componentCatalog:[{kind:"header",label:"文字页首",allowed:true},{kind:"section-list",label:"内容列表",allowed:true},{kind:"divider",label:"发丝线",allowed:true},{kind:"custom-fallback",label:"自定义模块兜底",allowed:true}],bindings,editableParameters:{...editableParameters,accentColor:{type:"color",default:"#202020",locked:true},marginMm:{type:"number",min:16,max:28,step:1,default:20,locked:false}},fallback,continuation:{kind:"single-flow",repeatHeader:"name-only"}},
    componentVersions,assetManifest:[],
  },
];

export type SampleKind="short"|"long"|"fallback";
export function createCandidateSample(candidate:FormalTemplateCandidate,kind:SampleKind):ResumeDocument{
  const document=structuredClone(createBenchmarkDocument());
  document.documentId=`candidate-${candidate.stableId}-${kind}`;
  document.template={id:candidate.stableId,version:0};
  const visual=candidate.config.visualKey;
  document.layout.kind=visual==="centered"?"two-column":"single";
  document.layout.page.marginMm=visual==="bareline"?20:16;
  document.layout.theme.accentColor=visual==="centered"?"#242424":visual==="verdant"?"#315b52":"#202020";
  document.layout.theme.fontFamily="Noto Sans SC";
  document.layout.decorations=[];
  if(kind==="short") document.sections=document.sections.map(section=>({...section,items:section.items.slice(0,1)}));
  if(kind==="long"){
    const work=document.sections.find(section=>section.type==="experience");
    if(work)work.items=Array.from({length:8},(_,index)=>({...work.items[index%work.items.length],id:`long-${index}`,title:`${work.items[index%work.items.length].title} · 第 ${index+1} 段经历`,bullets:["负责跨部门复杂项目的规划、拆解与交付，持续跟踪质量、成本和业务结果。","建立可复用的工作机制并辅导团队成员，覆盖中文、English、数字 2026 与长内容分页。","通过数据复盘推动下一阶段迭代，保证每条经历在页面边界内完整呈现。"]}));
  }
  if(kind==="fallback")document.sections=[{id:"custom-research",type:"custom",title:"自定义研究",column:"main",visible:true,items:[{id:"unknown-fields",title:"开放课题",summary:"未知模块和字段仍以通用语义安全展示，不因模板不认识而静默丢失。",fields:[{id:"field-method",label:"研究方法",type:"short-text",value:"访谈 · 可用性测试"},{id:"field-result",label:"关键结果",type:"long-text",value:"完成跨领域样本验证并保留所有自定义内容。"}]}]}];
  return document;
}
