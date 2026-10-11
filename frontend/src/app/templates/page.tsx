"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {LoginBubble}from"@/features/auth/LoginBubble";
import{apiFetch,currentUser,type CurrentUser}from"@/features/auth/auth-client";
import{GENERAL_TEMPLATE_ID,GENERAL_TEMPLATE_NAME}from"@/resume/templates/general";

type Template = { id:string; name:string; category:string; tags:string[]; version:number };
const categories=["全部","极简","经典","现代","创意","应届生"];
export default function TemplateLibrary(){
  const [templates,setTemplates]=useState<Template[]>([]);const [category,setCategory]=useState("全部");const [error,setError]=useState("");const[user,setUser]=useState<CurrentUser|null>(null);const[favorites,setFavorites]=useState<string[]>([]);const[login,setLogin]=useState(false);
  useEffect(()=>{fetch("/api/templates",{cache:"no-store"}).then(async(response)=>{if(!response.ok)throw new Error("模板服务暂不可用");setTemplates(await response.json());}).catch((reason)=>setError(reason.message));currentUser().then(async value=>{setUser(value);if(value)setFavorites(await apiFetch<string[]>("/api/templates/favorites"));}).catch(()=>{});},[]);
  const toggleFavorite=async(templateId:string)=>{if(!user){setLogin(true);return;}const active=favorites.includes(templateId);await apiFetch(`/api/templates/${templateId}/favorite`,{method:active?"DELETE":"PUT"});setFavorites(values=>active?values.filter(id=>id!==templateId):[templateId,...values]);};
  const previewTemplate:Template={id:GENERAL_TEMPLATE_ID,name:GENERAL_TEMPLATE_NAME,category:"通用",tags:["结构清晰","直接编辑"],version:1};
  const allTemplates=[previewTemplate,...templates.filter(template=>template.id!==GENERAL_TEMPLATE_ID)];
  const visible=allTemplates.filter((template)=>category==="全部"||template.category===category);
  return <main className="template-page">
    <header className="library-header"><Link className="brand" href="/">Resumint</Link><nav><Link href="/templates">模板库</Link>{user?<Link href="/account">{user.maskedPhone}</Link>:<button onClick={()=>setLogin(true)}>游客模式 · 登录</button>}</nav></header>
    <section className="library-intro"><span className="eyebrow">模板库</span><h1>先看结构，再写内容</h1><p>模板缩略图隐藏实例文字，帮助你专注比较信息层级与版式；进入编辑后即可在简历上直接修改。</p></section>
    <div className="category-tabs">{categories.map((value)=><button className={value===category?"active":""} key={value} onClick={()=>setCategory(value)}>{value}</button>)}</div>
    {error&&templates.length>0&&<p className="library-error" role="alert">{error}</p>}
    <section className="template-grid">{visible.map((template)=><article className="template-card" key={template.id}><div className={`template-sheet ${template.id===GENERAL_TEMPLATE_ID?"general-skeleton":""}`}>{template.id===GENERAL_TEMPLATE_ID?<><div className="skeleton-identity"><i/><div><b/><span/></div></div><div className="skeleton-rule"/><div className="skeleton-overview"><div><b/><span/><span/><span/></div><div><b/><span/><span/><span/></div></div><div className="skeleton-section"><b/><strong/><span/><strong/><span/></div><div className="skeleton-section"><b/><strong/><span/><span/></div></>:<><div className="mini-header"/><div className="mini-title"/><div className="mini-line"/><div className="mini-columns"><i/><i/></div></>}<Link href={`/editor?template=${template.id}`} className="use-template">使用此模板</Link></div><div className="template-meta"><div><strong>{template.name}</strong><span>{template.category} · {template.id===GENERAL_TEMPLATE_ID?"待确认":"v"+template.version}</span></div><button className={favorites.includes(template.id)?"favorite-active":""} aria-label={favorites.includes(template.id)?"取消收藏":"收藏模板"} title={user?"收藏模板":"登录后可收藏"} onClick={()=>void toggleFavorite(template.id)}>{favorites.includes(template.id)?"♥":"♡"}</button></div><div className="tags">{template.tags.map((tag)=><span key={tag}>{tag}</span>)}</div></article>)}</section>
    <LoginBubble open={login} onClose={()=>setLogin(false)} onLogin={value=>{setUser(value);setLogin(false);void apiFetch<string[]>("/api/templates/favorites").then(setFavorites);}}/>
  </main>;
}
