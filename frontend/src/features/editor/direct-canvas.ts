import type { CSSProperties } from "react";
import type { ElementStyle, ResumeDocument, ResumeItem, ResumeSection } from "@/resume/model";

export function moveById<T extends {id:string}>(items:T[],sourceId:string,targetId:string):T[]{
  const from=items.findIndex(item=>item.id===sourceId);const to=items.findIndex(item=>item.id===targetId);
  if(from<0||to<0||from===to)return items;
  const next=[...items];const[moved]=next.splice(from,1);next.splice(to,0,moved);return next;
}

export function removeById<T extends {id:string}>(items:T[],id:string):T[]{return items.filter(item=>item.id!==id);}

export function insertItemAfter(items:ResumeItem[],afterId:string|undefined,id:string):ResumeItem[]{
  const source=items.find(item=>item.id===afterId)??items.at(-1);
  const next:ResumeItem=source?{...structuredClone(source),id,title:`${source.title}（新增）`}:{id,title:"新增内容",summary:"双击这里填写内容。"};
  const at=source?items.findIndex(item=>item.id===source.id)+1:items.length;
  return [...items.slice(0,at),next,...items.slice(at)];
}

export function addContact(document:ResumeDocument,id:string):ResumeDocument{
  const next=structuredClone(document);(next.profile.contacts??=[]).push({id,label:"联系方式",value:"双击填写"});return next;
}

export function duplicateSectionAfter(sections:ResumeSection[],sourceId:string,sectionId:string,createId:(kind:"item"|"field"|"block")=>string,elementStyles?:Record<string,ElementStyle>,hiddenElementIds?:string[]):ResumeSection[]{
  const index=sections.findIndex(section=>section.id===sourceId);
  if(index<0)return sections;
  const source=structuredClone(sections[index]);
  const idMap=new Map<string,string>([[`section.${source.id}.`,`section.${sectionId}.`]]);
  const section:ResumeSection={...source,id:sectionId,items:source.items.map(item=>{const id=createId("item");idMap.set(`item.${item.id}.`,`item.${id}.`);return {...item,id,fields:item.fields?.map(field=>{const fieldId=createId("field");idMap.set(`field.${field.id}.`,`field.${fieldId}.`);return {...field,id:fieldId};}),richText:item.richText?.map(block=>{const blockId=createId("block");idMap.set(`block.${block.id}.`,`block.${blockId}.`);return {...block,id:blockId};})};})};
  if(elementStyles)for(const[key,style]of Object.entries({...elementStyles}))for(const[from,to]of idMap)if(key.startsWith(from)){elementStyles[`${to}${key.slice(from.length)}`]=structuredClone(style);break;}
  if(hiddenElementIds)for(const key of [...hiddenElementIds])for(const[from,to]of idMap)if(key.startsWith(from)){hiddenElementIds.push(`${to}${key.slice(from.length)}`);break;}
  return [...sections.slice(0,index+1),section,...sections.slice(index+1)];
}

export function elementStyle(document:ResumeDocument,id:string):ElementStyle{return document.elementStyles?.[id]??{};}

export function elementStyleCss(document:ResumeDocument,id:string):CSSProperties{
  const style=elementStyle(document,id);
  return {fontFamily:style.fontFamily?`'${style.fontFamily}', Arial, 'Microsoft YaHei', sans-serif`:undefined,fontWeight:style.fontWeight,fontSize:style.fontSizePt?`${style.fontSizePt}pt`:undefined,color:style.color,lineHeight:style.lineHeight,paddingBottom:style.spacingMm?`${style.spacingMm}mm`:undefined};
}

type RenderedStyleSource=Pick<CSSStyleDeclaration,"fontFamily"|"fontWeight"|"fontSize"|"color"|"lineHeight"|"paddingBottom">;

function cssColorToHex(value:string):string{
  if(/^#[0-9a-f]{6}$/i.test(value))return value.toLowerCase();
  const channels=value.match(/[\d.]+/g)?.slice(0,3).map(Number);
  return channels?.length===3?`#${channels.map(channel=>Math.max(0,Math.min(255,Math.round(channel))).toString(16).padStart(2,"0")).join("")}`:"#000000";
}

export function normalizeRenderedElementStyle(style:RenderedStyleSource):Required<ElementStyle>{
  const fontSizePx=Number.parseFloat(style.fontSize)||12;
  const numericFontWeight=Number.parseInt(style.fontWeight,10);
  const lineHeightPx=Number.parseFloat(style.lineHeight);
  const round=(value:number)=>Number(value.toFixed(2));
  return {
    fontFamily:style.fontFamily.split(",")[0].trim().replace(/^['"]|['"]$/g,""),
    fontWeight:Number.isFinite(numericFontWeight)?numericFontWeight:style.fontWeight==="bold"?700:400,
    fontSizePt:round(fontSizePx*72/96),
    color:cssColorToHex(style.color),
    lineHeight:Number.isFinite(lineHeightPx)?round(lineHeightPx/fontSizePx):1.2,
    spacingMm:round((Number.parseFloat(style.paddingBottom)||0)*25.4/96),
  };
}

export function readRenderedElementStyle(element:Element):Required<ElementStyle>{
  return normalizeRenderedElementStyle(getComputedStyle(element));
}

export function themeVariables(document:ResumeDocument):CSSProperties{
  return {"--accent":document.layout.theme.accentColor,"--font":`'${document.layout.theme.fontFamily}', Arial, 'Microsoft YaHei', sans-serif`,"--body-font-size":`${document.layout.theme.fontSizePt??8.5}pt`,"--body-line-height":document.layout.theme.lineHeight??1.48} as CSSProperties;
}
