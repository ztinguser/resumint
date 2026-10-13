"use client";

import {themeVariables} from "@/features/editor/direct-canvas";
import {isElementVisible} from "./element-visibility";
import {DirectSection,EditableText,PrintBlocks,type DirectTemplatePageProps} from "./GeneralTemplatePage";
import type {RenderPage} from "./pagination";

type Props=DirectTemplatePageProps&{printPage?:RenderPage};

export function BarelineTemplatePage(props:Props){
  const {document,editable,selectedSectionId,selectedElementId,pageNumber=1,printPage}=props;
  const profile=document.profile;
  const side=document.sections.filter(section=>section.visible&&section.column==="side");
  const main=document.sections.filter(section=>section.visible&&section.column==="main");
  const text=(id:string,value:string,label:string,onCommit:(value:string)=>void,className?:string)=>isElementVisible(document,id)?<EditableText document={document} elementId={id} selected={selectedElementId===id} onSelect={props.onSelectElement} value={value} label={label} onCommit={onCommit} className={className}/>:null;
  const section=(value:typeof document.sections[number],sectionCount:number)=><DirectSection key={value.id} document={document} section={value} editable={editable} selected={value.id===selectedSectionId} selectedElementId={selectedElementId} onSelect={()=>props.onSelectSection?.(value.id)} onSelectElement={props.onSelectElement} onChange={next=>props.onSectionChange?.(value.id,"title",next)} onItemChange={(item,key,next)=>props.onItemChange?.(value.id,item.id,key,next)} onAddItem={after=>props.onAddItem?.(value.id,after)} onDeleteItem={itemId=>props.onDeleteItem?.(value.id,itemId)} onAddSection={()=>props.onAddSection?.(value.id)} onDeleteSection={sectionCount>1?()=>props.onDeleteSection?.(value.id):undefined} onMoveItem={(from,to)=>props.onMoveItem?.(value.id,from,to)} onMoveSection={props.onMoveSection}/>;
  return <article className={`resume-page bareline-template-page ${editable?"is-editable":"is-print-page"}`} style={themeVariables(document)} data-page={pageNumber} onClick={editable?props.onClearSelection:undefined}>
    {pageNumber===1?<header className="bareline-header"><div className="bareline-identity">{text("profile.fullName",profile.fullName,"姓名",value=>props.onProfileChange?.("fullName",value),"bareline-name")}{text("profile.headline",profile.headline,"职业标题",value=>props.onProfileChange?.("headline",value),"bareline-headline")}</div></header>:<header className="bareline-continuation"><strong>{profile.fullName}</strong><span>{profile.headline} · 第 {pageNumber} 页</span></header>}
    <div className="bareline-columns">
      <aside className="bareline-side">{pageNumber===1&&<section className="bareline-contacts">{isElementVisible(document,"profile.contactTitle")&&<EditableText document={document} elementId="profile.contactTitle" selected={selectedElementId==="profile.contactTitle"} onSelect={props.onSelectElement} value={profile.contactTitle??"联系方式"} label="联系方式标题" tag="h2" onCommit={value=>props.onProfileChange?.("contactTitle",value)}/>}<div className="bareline-contact-list">{(profile.contacts??[]).map(contact=>isElementVisible(document,`contact.${contact.id}.value`)?<div className="bareline-contact" key={contact.id}>{text(`contact.${contact.id}.value`,contact.value,"联系方式内容",value=>props.onContactChange?.(contact.id,"value",value),contact.href?"bareline-contact-link":"bareline-contact-value")}{editable&&<button className="contact-delete" type="button" aria-label={`删除联系方式 ${contact.value}`} onClick={event=>{event.stopPropagation();props.onDeleteContact?.(contact.id);}}>×</button>}</div>:null)}{editable&&<button className="contact-add" type="button" aria-label="新增联系方式" title="新增联系方式" onClick={event=>{event.stopPropagation();props.onAddContact?.();}}>＋</button>}</div></section>}{printPage?<PrintBlocks document={document} blocks={printPage.side}/>:side.map(value=>section(value,document.sections.length))}</aside>
      <main className="bareline-main">{printPage?<PrintBlocks document={document} blocks={printPage.main}/>:main.map(value=>section(value,document.sections.length))}</main>
    </div>
  </article>;
}
