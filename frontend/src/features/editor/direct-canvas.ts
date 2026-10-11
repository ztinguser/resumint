import type { CSSProperties } from "react";
import type { ElementStyle, ResumeDocument, ResumeItem, ResumeSection } from "@/resume/model";

export function moveById<T extends {id:string}>(items:T[],sourceId:string,targetId:string):T[]{
  const from=items.findIndex(item=>item.id===sourceId);const to=items.findIndex(item=>item.id===targetId);
  if(from<0||to<0||from===to)return items;
  const next=[...items];const[moved]=next.splice(from,1);next.splice(to,0,moved);return next;
}

export function insertItemAfter(items:ResumeItem[],afterId:string|undefined,id:string):ResumeItem[]{
  const source=items.find(item=>item.id===afterId)??items.at(-1);
  const next:ResumeItem=source?{...structuredClone(source),id,title:`${source.title}（新增）`}:{id,title:"新增内容",summary:"双击这里填写内容。"};
  const at=source?items.findIndex(item=>item.id===source.id)+1:items.length;
  return [...items.slice(0,at),next,...items.slice(at)];
}

export function addContact(document:ResumeDocument,id:string):ResumeDocument{
  const next=structuredClone(document);(next.profile.contacts??=[]).push({id,label:"联系方式",value:"双击填写"});return next;
}

const sectionNames:Record<ResumeSection["type"],string>={experience:"工作经历",education:"教育经历",project:"项目经历",skills:"技能",custom:"自定义模块"};

export function insertSectionAfter(sections:ResumeSection[],afterId:string,type:ResumeSection["type"],sectionId:string,itemId:string):ResumeSection[]{
  const title=sectionNames[type];
  const section:ResumeSection={id:sectionId,type,title,column:"main",visible:true,items:[{id:itemId,title:type==="custom"?"自定义内容":`新的${title}`,summary:"双击这里填写内容。"}]};
  const index=sections.findIndex(item=>item.id===afterId);
  const at=index<0?sections.length:index+1;
  return [...sections.slice(0,at),section,...sections.slice(at)];
}

export function elementStyle(document:ResumeDocument,id:string):ElementStyle{return document.elementStyles?.[id]??{};}

export function elementStyleCss(document:ResumeDocument,id:string):CSSProperties{
  const style=elementStyle(document,id);
  return {fontFamily:style.fontFamily?`'${style.fontFamily}', Arial, 'Microsoft YaHei', sans-serif`:undefined,fontSize:style.fontSizePt?`${style.fontSizePt}pt`:undefined,color:style.color,lineHeight:style.lineHeight,paddingBottom:style.spacingMm?`${style.spacingMm}mm`:undefined};
}

export function themeVariables(document:ResumeDocument):CSSProperties{
  return {"--accent":document.layout.theme.accentColor,"--font":`'${document.layout.theme.fontFamily}', Arial, 'Microsoft YaHei', sans-serif`,"--body-font-size":`${document.layout.theme.fontSizePt??8.5}pt`,"--body-line-height":document.layout.theme.lineHeight??1.48} as CSSProperties;
}
