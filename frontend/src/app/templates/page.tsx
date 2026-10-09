"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

type Template = { id:string; name:string; category:string; tags:string[]; version:number };
const categories=["全部","极简","经典","现代","创意","应届生"];
export default function TemplateLibrary(){
  const [templates,setTemplates]=useState<Template[]>([]);const [category,setCategory]=useState("全部");const [error,setError]=useState("");
  useEffect(()=>{fetch("/api/templates",{cache:"no-store"}).then(async(response)=>{if(!response.ok)throw new Error("模板服务暂不可用");setTemplates(await response.json());}).catch((reason)=>setError(reason.message));},[]);
  const visible=templates.filter((template)=>category==="全部"||template.category===category);
  return <main className="template-page">
    <header className="library-header"><Link className="brand" href="/">Resumint</Link><nav><Link href="/templates">模板库</Link><button>游客模式</button></nav></header>
    <section className="library-intro"><span className="eyebrow">模板库</span><h1>从一个可靠的结构开始</h1><p>M3 使用排版基准模板打通游客编辑与导出；正式的通用、商务、简约模板将在 M5 制作。</p></section>
    <div className="category-tabs">{categories.map((value)=><button className={value===category?"active":""} key={value} onClick={()=>setCategory(value)}>{value}</button>)}</div>
    {error&&<p className="library-error" role="alert">{error}</p>}
    <section className="template-grid">{visible.map((template)=><article className="template-card" key={template.id}><div className="template-sheet"><div className="mini-header"/><div className="mini-title"/><div className="mini-line"/><div className="mini-columns"><i/><i/></div><Link href={`/editor?template=${template.id}`} className="use-template">使用此模板</Link></div><div className="template-meta"><div><strong>{template.name}</strong><span>{template.category} · v{template.version}</span></div><button aria-label="收藏需要登录" title="登录后可收藏">♡</button></div><div className="tags">{template.tags.map((tag)=><span key={tag}>{tag}</span>)}</div></article>)}</section>
  </main>;
}
