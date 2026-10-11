"use client";

import { useEffect, useRef, useState } from "react";
import {themeVariables}from"@/features/editor/direct-canvas";
import type { ResumeDocument, ResumeItem, ResumeSection } from "./model";
import type {RenderBlock}from"./pagination";

type Props = {document:ResumeDocument;editable?:boolean;printBlocks?:RenderBlock[];pageNumber?:number;selectedSectionId?:string;onSelectSection?:(id:string)=>void;onProfileChange?:(key:"fullName"|"headline"|"email"|"phone"|"location",value:string)=>void;onWebsiteChange?:(value:string)=>void;onContactChange?:(id:string,key:"label"|"value",value:string)=>void;onAddContact?:()=>void;onSectionChange?:(sectionId:string,key:"title",value:string)=>void;onItemChange?:(sectionId:string,itemId:string,key:"title"|"subtitle"|"date"|"summary",value:string)=>void;onAddItem?:(sectionId:string,afterItemId?:string)=>void;onMoveSection?:(fromId:string,toId:string)=>void;onMoveItem?:(sectionId:string,fromId:string,toId:string)=>void};

function EditableText({value,onCommit,className,multiline=false,label}:{value:string;onCommit?:(value:string)=>void;className?:string;multiline?:boolean;label:string}){
  const[editing,setEditing]=useState(false);const ref=useRef<HTMLElement>(null);
  useEffect(()=>{if(editing){ref.current?.focus();window.document.execCommand("selectAll",false);}},[editing]);
  const finish=()=>{const next=ref.current?.textContent?.trim()||value;setEditing(false);if(next!==value)onCommit?.(next);};const Tag=multiline?"div":"span";
  return <Tag ref={ref as never} className={`${className??""} ${onCommit?"direct-editable":""} ${editing?"is-editing":""}`} contentEditable={editing} suppressContentEditableWarning role={onCommit?"textbox":undefined} aria-label={onCommit?`${label}，双击编辑`:undefined} title={onCommit?"双击编辑":undefined} onDoubleClick={()=>onCommit&&setEditing(true)} onBlur={finish} onKeyDown={event=>{if(event.key==="Escape"){if(ref.current)ref.current.textContent=value;setEditing(false);}if(!multiline&&event.key==="Enter"){event.preventDefault();finish();}}}>{value}</Tag>;
}

function ContactLine({children}:{children:React.ReactNode}){return <li>{children}</li>;}

function PrintBlocks({blocks}:{blocks:RenderBlock[]}){return <div className="general-print-blocks">{blocks.map(block=>{
  if(block.kind==="section")return <h2 className="general-section-title" key={block.key}>{block.text}</h2>;
  if(block.kind==="item-heading"&&block.item)return <div className="general-entry-heading" key={block.key}><div><strong className="general-entry-title">{block.item.title}</strong>{block.item.subtitle&&<span className="general-entry-subtitle">{block.item.subtitle}</span>}</div>{block.item.date&&<time className="general-entry-date">{block.item.date}</time>}</div>;
  if((block.kind==="paragraph"||block.kind==="bullet")&&block.richText)return <p className={`general-entry-summary ${block.kind==="bullet"?"bullet":""}`} key={block.key}>{block.richText.spans.map((span,index)=>span.link?<a href={span.link} key={index}>{span.bold?<strong>{span.text}</strong>:span.text}</a>:span.bold?<strong key={index}>{span.text}</strong>:<span key={index}>{span.text}</span>)}</p>;
  if(block.kind==="links")return <div className="item-links" key={block.key}>{block.links?.map(link=><a href={link.href} key={link.href}>{link.label}</a>)}</div>;
  return <p className={`general-entry-summary ${block.kind==="bullet"?"bullet":""}`} key={block.key}>{block.text}</p>;
})}</div>;}

function Section({section,editable,selected,onSelect,onChange,onItemChange,onAddItem,onMoveItem,onMoveSection}:{section:ResumeSection;editable?:boolean;selected?:boolean;onSelect?:()=>void;onChange?:(value:string)=>void;onItemChange?:(item:ResumeItem,key:"title"|"subtitle"|"date"|"summary",value:string)=>void;onAddItem?:(afterItemId?:string)=>void;onMoveItem?:(fromId:string,toId:string)=>void;onMoveSection?:(fromId:string,toId:string)=>void}){
  const[dragging,setDragging]=useState<string|null>(null);const[dragTarget,setDragTarget]=useState<string|null>(null);const findTarget=(event:React.PointerEvent,selector:string,attribute:string)=>window.document.elementFromPoint(event.clientX,event.clientY)?.closest(selector)?.getAttribute(attribute)??null;
  const startSection=(event:React.PointerEvent)=>{if(!editable||(event.target as HTMLElement).closest("button,[contenteditable=true]"))return;event.currentTarget.setPointerCapture(event.pointerId);setDragging(section.id);};
  const moveSection=(event:React.PointerEvent)=>{if(dragging===section.id)setDragTarget(findTarget(event,"[data-section-id]","data-section-id"));};
  const endSection=(event:React.PointerEvent)=>{if(dragging!==section.id)return;const target=findTarget(event,"[data-section-id]","data-section-id");if(target&&target!==section.id)onMoveSection?.(section.id,target);setDragging(null);setDragTarget(null);};
  return <section data-section-id={section.id} className={`general-section ${selected?"is-selected":""} ${dragging?"is-dragging":""} ${dragTarget===section.id?"is-drop-target":""}`} onClick={onSelect} onPointerDown={startSection} onPointerMove={moveSection} onPointerUp={endSection}>
    {editable&&<span className="canvas-grip" role="button" tabIndex={0} aria-label={`拖动${section.title}排序`} title="拖动排序">⠿</span>}
    <EditableText className="general-section-title" value={section.title} onCommit={onChange} label="分区标题"/>
    <div className="general-section-items">{section.items.map(item=><article data-item-id={item.id} className={`general-entry ${dragTarget===item.id?"is-drop-target":""}`} key={item.id} onPointerDown={event=>{if(!editable||(event.target as HTMLElement).closest("button,[contenteditable=true]"))return;event.stopPropagation();event.currentTarget.setPointerCapture(event.pointerId);setDragging(item.id);}} onPointerMove={event=>{if(dragging===item.id){event.stopPropagation();setDragTarget(findTarget(event,"[data-item-id]","data-item-id"));}}} onPointerUp={event=>{if(dragging===item.id){event.stopPropagation();const target=findTarget(event,"[data-item-id]","data-item-id");if(target&&target!==item.id)onMoveItem?.(item.id,target);setDragging(null);setDragTarget(null);}}}>
      {editable&&<span className="entry-grip" aria-hidden>⠿</span>}
      {(section.type==="experience"||section.type==="education")&&<div className="general-entry-heading"><div><EditableText className="general-entry-title" value={item.title} onCommit={value=>onItemChange?.(item,"title",value)} label="条目名称"/>{item.subtitle&&<EditableText className="general-entry-subtitle" value={item.subtitle} onCommit={value=>onItemChange?.(item,"subtitle",value)} label="角色或专业"/>}</div>{item.date&&<EditableText className="general-entry-date" value={item.date} onCommit={value=>onItemChange?.(item,"date",value)} label="日期"/>}</div>}
      {item.summary&&<EditableText className="general-entry-summary" multiline value={item.summary} onCommit={value=>onItemChange?.(item,"summary",value)} label="描述"/>}
      {editable&&<button className="entry-add" type="button" aria-label={`在${item.title}后新增一项`} title="新增同类内容" onClick={event=>{event.stopPropagation();onAddItem?.(item.id);}}>＋</button>}
    </article>)}</div>
    {editable&&<button className="canvas-add" type="button" aria-label={`在${section.title}末尾新增一项`} title="在末尾新增同类内容" onClick={event=>{event.stopPropagation();onAddItem?.();}}>＋</button>}
  </section>;
}

export function GeneralTemplatePage(props:Props){
  const{document,editable,selectedSectionId,pageNumber=1,printBlocks}=props;const profile=document.profile;const intro=document.sections.find(section=>section.id==="general-profile"&&section.visible);const body=document.sections.filter(section=>section.id!=="general-profile"&&section.visible);const profileItem=intro?.items[0];
  return <article className={`resume-page general-template-page ${editable?"is-editable":"is-print-page"}`} style={themeVariables(document)} data-page={pageNumber}>
    {pageNumber===1?<><header className="general-header">{profile.portrait&&<div className="general-avatar">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={profile.portrait.src} alt={profile.portrait.alt}/>
    </div>}<div className="general-identity"><EditableText className="general-headline" value={profile.headline} onCommit={value=>props.onProfileChange?.("headline",value)} label="职业标题"/><EditableText className="general-name" value={profile.fullName} onCommit={value=>props.onProfileChange?.("fullName",value)} label="姓名"/></div></header>
    <section className="general-overview"><div className="general-profile-copy"><h2>个人简介</h2>{profileItem?.summary&&<EditableText multiline value={profileItem.summary} onCommit={value=>props.onItemChange?.(intro!.id,profileItem.id,"summary",value)} label="个人简介"/>}</div><div className="general-contact-copy"><h2>联系方式</h2><div className="contact-list-wrap"><ul>{profile.location&&<ContactLine><EditableText value={profile.location} onCommit={value=>props.onProfileChange?.("location",value)} label="所在地"/></ContactLine>}<ContactLine><EditableText value={profile.email} onCommit={value=>props.onProfileChange?.("email",value)} label="邮箱"/></ContactLine>{profile.phone&&<ContactLine><EditableText value={profile.phone} onCommit={value=>props.onProfileChange?.("phone",value)} label="电话"/></ContactLine>}{profile.website&&<ContactLine><EditableText value={profile.website.label} onCommit={props.onWebsiteChange} label="个人链接"/></ContactLine>}{(profile.contacts??[]).map(contact=><ContactLine key={contact.id}><EditableText className="contact-label" value={contact.label} onCommit={value=>props.onContactChange?.(contact.id,"label",value)} label="联系方式名称"/><span>：</span><EditableText value={contact.value} onCommit={value=>props.onContactChange?.(contact.id,"value",value)} label="联系方式内容"/></ContactLine>)}</ul>{editable&&<button className="contact-add" type="button" aria-label="新增联系方式" title="新增联系方式" onClick={props.onAddContact}>＋</button>}</div></div></section></>:<header className="general-continuation"><strong>{profile.fullName}</strong><span>{profile.headline} · 第 {pageNumber} 页</span></header>}
    {printBlocks?<PrintBlocks blocks={printBlocks}/>:<div className="general-body">{body.map(section=><Section key={section.id} section={section} editable={editable} selected={section.id===selectedSectionId} onSelect={()=>props.onSelectSection?.(section.id)} onChange={value=>props.onSectionChange?.(section.id,"title",value)} onItemChange={(item,key,value)=>props.onItemChange?.(section.id,item.id,key,value)} onAddItem={after=>props.onAddItem?.(section.id,after)} onMoveItem={(from,to)=>props.onMoveItem?.(section.id,from,to)} onMoveSection={props.onMoveSection}/>)}</div>}
  </article>;
}
