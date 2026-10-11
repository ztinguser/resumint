"use client";

import { useEffect, useRef, useState } from "react";
import { elementStyleCss, themeVariables } from "@/features/editor/direct-canvas";
import type { ResumeDocument, ResumeItem, ResumeSection } from "./model";
import type { RenderBlock } from "./pagination";

type EditableKey = "fullName"|"headline"|"contactTitle"|"email"|"phone"|"location";
type Props = {
  document:ResumeDocument; editable?:boolean; printBlocks?:RenderBlock[]; pageNumber?:number;
  selectedSectionId?:string; selectedElementId?:string;
  onSelectSection?:(id:string)=>void; onSelectElement?:(id:string,label:string)=>void;
  onProfileChange?:(key:EditableKey,value:string)=>void; onWebsiteChange?:(value:string)=>void;
  onContactChange?:(id:string,key:"label"|"value",value:string)=>void; onAddContact?:()=>void;
  onSectionChange?:(sectionId:string,key:"title",value:string)=>void;
  onItemChange?:(sectionId:string,itemId:string,key:"title"|"subtitle"|"date"|"summary",value:string)=>void;
  onAddItem?:(sectionId:string,afterItemId?:string)=>void;
  onAddSection?:(afterSectionId:string,type:ResumeSection["type"])=>void;
  onMoveSection?:(fromId:string,toId:string)=>void; onMoveItem?:(sectionId:string,fromId:string,toId:string)=>void;
};

function EditableText({document,elementId,value,onCommit,onSelect,selected,className,multiline=false,tag,label}:{document:ResumeDocument;elementId:string;value:string;onCommit?:(value:string)=>void;onSelect?:(id:string,label:string)=>void;selected?:boolean;className?:string;multiline?:boolean;tag?:"span"|"div"|"h2";label:string}){
  const [editing,setEditing]=useState(false); const ref=useRef<HTMLElement>(null);
  useEffect(()=>{if(editing){ref.current?.focus();window.document.execCommand("selectAll",false);}},[editing]);
  const finish=()=>{const next=ref.current?.textContent?.trim()||value;setEditing(false);if(next!==value)onCommit?.(next);};
  const Tag=tag??(multiline?"div":"span");
  return <Tag ref={ref as never} style={elementStyleCss(document,elementId)} data-element-id={elementId} className={`${className??""} ${onCommit?"direct-editable":""} ${editing?"is-editing":""} ${selected?"is-element-selected":""}`} contentEditable={editing} suppressContentEditableWarning role={onCommit?"textbox":undefined} aria-label={onCommit?`${label}，双击编辑`:undefined} title={onCommit?"双击编辑":undefined} onClick={event=>{event.stopPropagation();onSelect?.(elementId,label);}} onDoubleClick={()=>{onSelect?.(elementId,label);if(onCommit)setEditing(true);}} onBlur={finish} onKeyDown={event=>{if(event.key==="Escape"){if(ref.current)ref.current.textContent=value;setEditing(false);}if(!multiline&&event.key==="Enter"){event.preventDefault();finish();}}}>{value}</Tag>;
}

function PrintBlocks({document,blocks}:{document:ResumeDocument;blocks:RenderBlock[]}){return <div className="general-print-blocks">{blocks.map(block=>{
  if(block.kind==="section"){const id=block.key.replace(/-title$/,"");return <h2 className="general-section-title" style={elementStyleCss(document,`section.${id}.title`)} key={block.key}>{block.text}</h2>;}
  if(block.kind==="item-heading"&&block.item){const id=block.item.id;return <div className="general-entry-heading" key={block.key}><div><strong className="general-entry-title" style={elementStyleCss(document,`item.${id}.title`)}>{block.item.title}</strong>{block.item.subtitle&&<span className="general-entry-subtitle" style={elementStyleCss(document,`item.${id}.subtitle`)}>{block.item.subtitle}</span>}</div>{block.item.date&&<time className="general-entry-date" style={elementStyleCss(document,`item.${id}.date`)}>{block.item.date}</time>}</div>;}
  if((block.kind==="paragraph"||block.kind==="bullet")&&block.richText)return <p className={`general-entry-summary ${block.kind==="bullet"?"bullet":""}`} key={block.key}>{block.richText.spans.map((span,index)=>span.link?<a href={span.link} key={index}>{span.bold?<strong>{span.text}</strong>:span.text}</a>:span.bold?<strong key={index}>{span.text}</strong>:<span key={index}>{span.text}</span>)}</p>;
  if(block.kind==="links")return <div className="item-links" key={block.key}>{block.links?.map(link=><a href={link.href} key={link.href}>{link.label}</a>)}</div>;
  const itemId=block.key.split("-summary-")[0];
  return <p className={`general-entry-summary ${block.kind==="bullet"?"bullet":""}`} style={block.kind==="paragraph"?elementStyleCss(document,`item.${itemId}.summary`):undefined} key={block.key}>{block.text}</p>;
})}</div>;}

const moduleOptions:Array<{type:ResumeSection["type"];label:string}>=[{type:"experience",label:"工作经历"},{type:"education",label:"教育经历"},{type:"project",label:"项目经历"},{type:"skills",label:"技能"},{type:"custom",label:"自定义模块"}];

function Section({document,section,editable,selected,selectedElementId,onSelect,onSelectElement,onChange,onItemChange,onAddItem,onAddSection,onMoveItem,onMoveSection}:{document:ResumeDocument;section:ResumeSection;editable?:boolean;selected?:boolean;selectedElementId?:string;onSelect?:()=>void;onSelectElement?:(id:string,label:string)=>void;onChange?:(value:string)=>void;onItemChange?:(item:ResumeItem,key:"title"|"subtitle"|"date"|"summary",value:string)=>void;onAddItem?:(afterItemId?:string)=>void;onAddSection?:(type:ResumeSection["type"])=>void;onMoveItem?:(fromId:string,toId:string)=>void;onMoveSection?:(fromId:string,toId:string)=>void}){
  const [dragging,setDragging]=useState<string|null>(null); const [dragTarget,setDragTarget]=useState<string|null>(null); const [moduleMenuOpen,setModuleMenuOpen]=useState(false);
  const findTarget=(event:React.PointerEvent,selector:string,attribute:string)=>window.document.elementFromPoint(event.clientX,event.clientY)?.closest(selector)?.getAttribute(attribute)??null;
  const blocked=(event:React.PointerEvent)=>(event.target as HTMLElement).closest("button,.direct-editable,[contenteditable=true]");
  const startSection=(event:React.PointerEvent)=>{if(!editable||blocked(event))return;event.currentTarget.setPointerCapture(event.pointerId);setDragging(section.id);};
  const moveSection=(event:React.PointerEvent)=>{if(dragging===section.id)setDragTarget(findTarget(event,"[data-section-id]","data-section-id"));};
  const endSection=(event:React.PointerEvent)=>{if(dragging!==section.id)return;const target=findTarget(event,"[data-section-id]","data-section-id");if(target&&target!==section.id)onMoveSection?.(section.id,target);setDragging(null);setDragTarget(null);};
  const text=(id:string,value:string,label:string,onCommit:(value:string)=>void,className?:string,multiline=false)=><EditableText document={document} elementId={id} selected={selectedElementId===id} onSelect={onSelectElement} value={value} label={label} onCommit={onCommit} className={className} multiline={multiline}/>;
  return <section data-section-id={section.id} className={`general-section ${selected?"is-selected":""} ${dragging?"is-dragging":""} ${dragTarget===section.id?"is-drop-target":""}`} onClick={onSelect} onPointerDown={startSection} onPointerMove={moveSection} onPointerUp={endSection}>
    {editable&&<span className="canvas-grip" role="button" tabIndex={0} aria-label={`拖动${section.title}排序`} title="拖动排序">⠿</span>}
    {text(`section.${section.id}.title`,section.title,"分区标题",value=>onChange?.(value),"general-section-title")}
    <div className="general-section-items">{section.items.map(item=><article data-item-id={item.id} className={`general-entry ${dragTarget===item.id?"is-drop-target":""}`} key={item.id} onPointerDown={event=>{if(!editable||blocked(event))return;event.stopPropagation();event.currentTarget.setPointerCapture(event.pointerId);setDragging(item.id);}} onPointerMove={event=>{if(dragging===item.id){event.stopPropagation();setDragTarget(findTarget(event,"[data-item-id]","data-item-id"));}}} onPointerUp={event=>{if(dragging===item.id){event.stopPropagation();const target=findTarget(event,"[data-item-id]","data-item-id");if(target&&target!==item.id)onMoveItem?.(item.id,target);setDragging(null);setDragTarget(null);}}}>
      {editable&&<span className="entry-grip" aria-hidden>⠿</span>}
      {(section.type==="experience"||section.type==="education"||section.type==="project")&&<div className="general-entry-heading"><div>{text(`item.${item.id}.title`,item.title,"条目名称",value=>onItemChange?.(item,"title",value),"general-entry-title")}{item.subtitle&&text(`item.${item.id}.subtitle`,item.subtitle,"角色或专业",value=>onItemChange?.(item,"subtitle",value),"general-entry-subtitle")}</div>{item.date&&text(`item.${item.id}.date`,item.date,"日期",value=>onItemChange?.(item,"date",value),"general-entry-date")}</div>}
      {section.type!=="experience"&&section.type!=="education"&&section.type!=="project"&&item.title&&text(`item.${item.id}.title`,item.title,"条目名称",value=>onItemChange?.(item,"title",value),"general-entry-title")}
      {item.summary&&text(`item.${item.id}.summary`,item.summary,"描述",value=>onItemChange?.(item,"summary",value),"general-entry-summary",true)}
      {editable&&<button className="entry-add" type="button" aria-label={`在${item.title}后新增一项`} title="新增同类条目" onClick={event=>{event.stopPropagation();onAddItem?.(item.id);}}>＋</button>}
    </article>)}</div>
    {editable&&<div className="module-add-wrap"><button className="canvas-add" type="button" aria-label={`在${section.title}后新增模块`} title="新增完整模块" aria-expanded={moduleMenuOpen} onClick={event=>{event.stopPropagation();setModuleMenuOpen(value=>!value);}}>＋</button>{moduleMenuOpen&&<div className="module-add-menu" role="menu" aria-label="选择新模块类型">{moduleOptions.map(option=><button role="menuitem" key={option.type} onClick={event=>{event.stopPropagation();onAddSection?.(option.type);setModuleMenuOpen(false);}}>{option.label}</button>)}</div>}</div>}
  </section>;
}

export function GeneralTemplatePage(props:Props){
  const {document,editable,selectedSectionId,selectedElementId,pageNumber=1,printBlocks}=props; const profile=document.profile;
  const intro=document.sections.find(section=>section.id==="general-profile"&&section.visible); const body=document.sections.filter(section=>section.id!=="general-profile"&&section.visible); const profileItem=intro?.items[0];
  const text=(id:string,value:string,label:string,onCommit:(value:string)=>void,className?:string,multiline=false)=><EditableText document={document} elementId={id} selected={selectedElementId===id} onSelect={props.onSelectElement} value={value} label={label} onCommit={onCommit} className={className} multiline={multiline}/>;
  return <article className={`resume-page general-template-page ${editable?"is-editable":"is-print-page"}`} style={themeVariables(document)} data-page={pageNumber}>
    {pageNumber===1?<><header className="general-header">{profile.portrait&&<div className="general-avatar">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={profile.portrait.src} alt={profile.portrait.alt}/>
    </div>}<div className="general-identity">{text("profile.headline",profile.headline,"职业标题",value=>props.onProfileChange?.("headline",value),"general-headline")}{text("profile.fullName",profile.fullName,"姓名",value=>props.onProfileChange?.("fullName",value),"general-name")}</div></header>
    <section className="general-overview"><div className="general-profile-copy"><EditableText document={document} elementId={`section.${intro?.id??"general-profile"}.title`} selected={selectedElementId===`section.${intro?.id??"general-profile"}.title`} onSelect={props.onSelectElement} value={intro?.title??"个人简介"} label="个人简介标题" tag="h2" onCommit={value=>intro&&props.onSectionChange?.(intro.id,"title",value)}/>{profileItem?.summary&&text(`item.${profileItem.id}.summary`,profileItem.summary,"个人简介",value=>props.onItemChange?.(intro!.id,profileItem.id,"summary",value),undefined,true)}</div><div className="general-contact-copy"><EditableText document={document} elementId="profile.contactTitle" selected={selectedElementId==="profile.contactTitle"} onSelect={props.onSelectElement} value={profile.contactTitle??"联系方式"} label="联系方式标题" tag="h2" onCommit={value=>props.onProfileChange?.("contactTitle",value)}/><div className="contact-list-wrap"><ul>{profile.location&&<li>{text("profile.location",profile.location,"所在地",value=>props.onProfileChange?.("location",value))}</li>}<li>{text("profile.email",profile.email,"邮箱",value=>props.onProfileChange?.("email",value))}</li>{profile.phone&&<li>{text("profile.phone",profile.phone,"电话",value=>props.onProfileChange?.("phone",value))}</li>}{profile.website&&<li>{text("profile.website",profile.website.label,"个人链接",value=>props.onWebsiteChange?.(value))}</li>}{(profile.contacts??[]).map(contact=><li key={contact.id}>{text(`contact.${contact.id}.value`,contact.value,"联系方式",value=>props.onContactChange?.(contact.id,"value",value))}</li>)}</ul>{editable&&<button className="contact-add" type="button" aria-label="新增联系方式" title="新增联系方式" onClick={props.onAddContact}>＋</button>}</div></div></section></>:<header className="general-continuation"><strong>{profile.fullName}</strong><span>{profile.headline} · 第 {pageNumber} 页</span></header>}
    {printBlocks?<PrintBlocks document={document} blocks={printBlocks}/>:<div className="general-body">{body.map(section=><Section key={section.id} document={document} section={section} editable={editable} selected={section.id===selectedSectionId} selectedElementId={selectedElementId} onSelect={()=>props.onSelectSection?.(section.id)} onSelectElement={props.onSelectElement} onChange={value=>props.onSectionChange?.(section.id,"title",value)} onItemChange={(item,key,value)=>props.onItemChange?.(section.id,item.id,key,value)} onAddItem={after=>props.onAddItem?.(section.id,after)} onAddSection={type=>props.onAddSection?.(section.id,type)} onMoveItem={(from,to)=>props.onMoveItem?.(section.id,from,to)} onMoveSection={props.onMoveSection}/>)}</div>}
  </article>;
}
