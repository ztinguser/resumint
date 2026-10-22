"use client";

import Link from "next/link";
import {useEffect,useMemo,useState} from "react";
import {apiFetch,currentUser,type CurrentUser} from "@/features/auth/auth-client";
import {ResumePrintDocument} from "@/resume/ResumePrintDocument";
import {createCandidateSample,formalTemplateCandidates,type FormalTemplateCandidate,type SampleKind} from "@/resume/templates/formal-drafts";

type LayoutNode={id:string;kind:string;label:string;locked:boolean;children?:LayoutNode[]};
type Binding={kind:string;target:string;emptyBehavior:string};
type Config={visualKey:string;layoutTree:{type:string;children:LayoutNode[]};componentCatalog:Array<{kind:string;label:string;allowed:boolean}>;bindings:Binding[];editableParameters:Record<string,{type:string;min?:number;max?:number;step?:number;default:string|number;locked:boolean}>;fallback:{unknownModule:string;unknownField:string};continuation:Record<string,string>};
type Draft={id:string;displayName:string;tags:string[];listed:boolean;currentVersion:number|null;baseVersion:number|null;schemaVersion:string;config:Config;componentVersions:Record<string,number>;assetManifest:unknown[];revision:number;updatedAt:string};
type Audit={id:string;action:string;details:Record<string,unknown>;createdAt:string};
type PublishedVersion={templateId:string;version:number;publishedAt:string};

const actionNames:Record<string,string>={CREATED:"创建模板",DRAFT_SAVED:"保存草稿",VERSION_PUBLISHED:"发布版本",RENAMED:"修改展示名",LISTED:"上架",UNLISTED:"下架"};

export function TemplateWorkbench(){
  const [user,setUser]=useState<CurrentUser|null|undefined>();
  const [candidate,setCandidate]=useState(formalTemplateCandidates[0]);
  const [draft,setDraft]=useState<Draft|null>(null);
  const [displayName,setDisplayName]=useState(formalTemplateCandidates[0].recommendedName);
  const [config,setConfig]=useState<Config>(candidate.config as Config);
  const [selected,setSelected]=useState("header");
  const [sample,setSample]=useState<SampleKind>("short");
  const [audit,setAudit]=useState<Audit[]>([]);
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState("三套模板尚未创建发布记录；确认展示名后即可逐套保存、发布和上架。");
  useEffect(()=>{currentUser().then(setUser).catch(()=>setUser(null));},[]);
  const sampleDocument=useMemo(()=>createCandidateSample(candidate,sample),[candidate,sample]);
  const nodes=useMemo(()=>flatten(config.layoutTree.children),[config]);
  const selectedNode=nodes.find(node=>node.id===selected)??nodes[0];
  const switchCandidate=(next:FormalTemplateCandidate)=>{setCandidate(next);setDraft(null);setDisplayName(next.recommendedName);setConfig(structuredClone(next.config) as Config);setSelected("header");setAudit([]);setMessage("候选已切换。请加载已有草稿，或创建新草稿。");};
  const changeNode=(patch:Partial<LayoutNode>)=>setConfig(value=>({...value,layoutTree:{...value.layoutTree,children:mapNodes(value.layoutTree.children,node=>node.id===selected?{...node,...patch}:node)}}));
  const applyLoaded=async(value:Draft)=>{setDraft(value);setDisplayName(value.displayName);setConfig(value.config);setAudit(await apiFetch<Audit[]>(`/api/admin/templates/${value.id}/audit`));return value;};
  const load=async()=>{setBusy(true);try{const value=await apiFetch<Draft>(`/api/admin/templates/${candidate.stableId}`);await applyLoaded(value);setMessage(`已加载草稿 r${value.revision}${value.listed?`，线上版本 v${value.currentVersion}`:""}`);}catch{setMessage("后端尚无该候选草稿，可先检查视觉，再创建草稿。");}finally{setBusy(false);}};
  const create=async()=>{setBusy(true);try{const value=await apiFetch<Draft>("/api/admin/templates",{method:"POST",body:JSON.stringify({id:candidate.stableId,displayName:displayName.trim(),tags:["正式模板"],initialConfig:config})});await applyLoaded(value);setMessage("草稿已创建。请显式保存完整配置后发布。");}catch(error){setMessage(error instanceof Error?error.message:"创建失败");}finally{setBusy(false);}};
  const save=async()=>{if(!draft){setMessage("请先创建或加载草稿");return;}setBusy(true);try{const value=await apiFetch<Draft>(`/api/admin/templates/${draft.id}/draft`,{method:"PUT",body:JSON.stringify({expectedRevision:draft.revision,schemaVersion:"1.0",config,componentVersions:candidate.componentVersions,assetManifest:candidate.assetManifest})});await applyLoaded(value);setMessage(`已显式保存草稿 r${value.revision}。`);}catch(error){setMessage(error instanceof Error?error.message:"保存失败");}finally{setBusy(false);}};
  const rename=async()=>{if(!draft)return;setBusy(true);try{const value=await apiFetch<Draft>(`/api/admin/templates/${draft.id}/name`,{method:"PUT",body:JSON.stringify({displayName:displayName.trim()})});await applyLoaded(value);setMessage(`展示名已更新为“${value.displayName}”，稳定 ID 与历史版本引用不变。`);}catch(error){setMessage(error instanceof Error?error.message:"改名失败");}finally{setBusy(false);}};
  const publish=async()=>{if(!draft)return;setBusy(true);try{const published=await apiFetch<PublishedVersion>(`/api/admin/templates/${draft.id}/publish`,{method:"POST",body:JSON.stringify({expectedRevision:draft.revision})});const value=await apiFetch<Draft>(`/api/admin/templates/${draft.id}`);await applyLoaded(value);setMessage(`不可变版本 v${published.version} 已发布；确认后可上架。`);}catch(error){setMessage(error instanceof Error?error.message:"发布失败");}finally{setBusy(false);}};
  const setListing=async(listed:boolean)=>{if(!draft)return;setBusy(true);try{const value=await apiFetch<Draft>(`/api/admin/templates/${draft.id}/listing`,{method:"PUT",body:JSON.stringify({listed,version:listed?draft.baseVersion:null})});await applyLoaded(value);setMessage(listed?`“${value.displayName}” v${value.currentVersion} 已上架。`:`“${value.displayName}”已下架；历史版本仍可供已有简历读取。`);}catch(error){setMessage(error instanceof Error?error.message:listed?"上架失败":"下架失败");}finally{setBusy(false);}};
  const dirty=!!draft&&JSON.stringify(config)!==JSON.stringify(draft.config);
  const needsMetadataSave=!!draft&&Object.keys(draft.componentVersions).length===0;
  const saveNeeded=dirty||needsMetadataSave;
  const releasePrepared=!!draft&&!dirty&&Object.keys(draft.componentVersions).length>0;
  if(user===undefined)return <main className="admin-gate">正在核验管理员身份…</main>;
  if(!user)return <main className="admin-gate"><h1>需要登录</h1><p>模板工作台仅向管理员开放。</p><Link href="/templates">返回模板库</Link></main>;
  if(user.role!=="ADMIN")return <main className="admin-gate"><span className="eyebrow">403 · Access denied</span><h1>无权访问模板工作台</h1><p>普通用户即使直接进入地址，也不能读取或修改模板草稿。</p><Link href="/account">返回个人空间</Link></main>;
  return <main className="admin-workbench">
    <header className="admin-topbar"><Link className="brand" href="/">Resumint</Link><strong>管理员模板工作台</strong><span>身份：{user.maskedPhone} · 管理员</span><button disabled={busy} onClick={()=>void load()}>加载草稿</button><button className="primary" disabled={busy||!draft||!saveNeeded} onClick={()=>void save()}>显式保存</button></header>
    <div className="approval-banner" role="status">{message}</div>
    <section className="candidate-strip">{formalTemplateCandidates.map((item,index)=><button key={item.stableId} className={candidate.stableId===item.stableId?"active":""} onClick={()=>switchCandidate(item)}><small>候选 {index+1}</small><strong>{item.recommendedName}</strong><span>{item.stableId}</span></button>)}</section>
    <div className="admin-grid">
      <aside className="admin-left">
        <section><div className="admin-section-title"><h2>受控组件</h2><button onClick={()=>void create()} disabled={busy||!!draft||displayName.trim().length<2}>创建草稿</button></div>{config.componentCatalog.map(item=><div className="catalog-row" key={item.kind}><span>{item.label}</span><code>{item.kind}</code></div>)}</section>
        <section><h2>布局树</h2><div className="layout-tree">{nodes.map(node=><button key={node.id} className={selected===node.id?"active":""} style={{paddingLeft:`${12+depth(config.layoutTree.children,node.id)*18}px`}} onClick={()=>setSelected(node.id)}><span>{node.locked?"▣":"□"}</span>{node.label}<code>{node.kind}</code></button>)}</div></section>
        <section><h2>字段绑定</h2>{config.bindings.map((binding,index)=><div className="binding-row" key={`${binding.kind}-${index}`}><strong>{binding.kind}</strong><code>{binding.target}</code><span>{binding.emptyBehavior}</span></div>)}</section>
      </aside>
      <section className="admin-preview"><div className="sample-tabs"><span>固定样本</span>{(["short","long","fallback"] as SampleKind[]).map(kind=><button className={sample===kind?"active":""} onClick={()=>setSample(kind)} key={kind}>{kind==="short"?"短内容":kind==="long"?"长内容 / 多页":"自定义字段兜底"}</button>)}</div><div className="candidate-summary"><div><span className="eyebrow">模板候选</span><h1>{draft?.displayName??candidate.recommendedName}</h1><p>{candidate.rationale}</p></div><dl><dt>稳定 ID</dt><dd>{candidate.stableId}</dd><dt>备选名</dt><dd>{candidate.alternateNames.join(" / ")}</dd><dt>发布状态</dt><dd>{draft?.listed?`已上架 · v${draft.currentVersion}`:draft?.baseVersion?`v${draft.baseVersion} 已发布 · 未上架`:draft?"草稿 · 未发布":"尚未创建"}</dd></dl></div><div className="admin-preview-scroll"><div className="admin-paper-scale"><ResumePrintDocument key={`${candidate.stableId}-${sample}`} initialDocument={sampleDocument}/></div></div></section>
      <aside className="admin-properties"><h2>属性面板</h2><label>正式展示名<input value={displayName} onChange={event=>setDisplayName(event.target.value)}/></label><button disabled={busy||!draft||displayName.trim()===draft.displayName||displayName.trim().length<2} onClick={()=>void rename()}>保存展示名</button>{selectedNode&&<><label>组件名称<input value={selectedNode.label} onChange={event=>changeNode({label:event.target.value})}/></label><label className="check"><input type="checkbox" checked={selectedNode.locked} onChange={event=>changeNode({locked:event.target.checked})}/> 锁定结构属性</label><label>组件类型<input value={selectedNode.kind} disabled/></label></>}<h3>编辑边界</h3>{Object.entries(config.editableParameters).map(([key,value])=><div className="boundary-row" key={key}><strong>{key}</strong><span>{value.locked?"锁定":`${value.min??"—"} – ${value.max??"—"}`}</span><code>默认 {String(value.default)}</code></div>)}<h3>素材与授权</h3><p className="asset-note">{candidate.assetPlan}</p><p className="asset-state">{candidate.assetManifest.length?`内容寻址素材：${candidate.assetManifest.length} 项`:"无模板级外部素材；使用系统字体与 CSS 图形"}</p><h3>版本与上架</h3><div className="release-actions"><button disabled={busy||!releasePrepared} onClick={()=>void publish()}>发布不可变版本</button><button disabled={busy||!draft?.baseVersion||draft.listed} onClick={()=>void setListing(true)}>上架</button><button disabled={busy||!draft?.listed} onClick={()=>void setListing(false)}>下架</button></div><small className="release-note">{dirty?"存在未保存配置；保存后才能发布。":needsMetadataSave?"请先显式保存组件版本和素材清单。":draft?.listed?`当前线上版本 v${draft.currentVersion}。`:draft?.baseVersion?`已发布 v${draft.baseVersion}，可上架或继续发布新版本。`:"完整配置保存后可发布；上架只指向已发布的不可变版本。"}</small><h3>审计</h3><ol className="audit-list">{audit.map(event=><li key={event.id}><strong>{actionNames[event.action]??event.action}</strong><time>{new Date(event.createdAt).toLocaleString("zh-CN")}</time></li>)}{!audit.length&&<li>尚无审计事件</li>}</ol></aside>
    </div>
  </main>;
}

function flatten(nodes:LayoutNode[]):LayoutNode[]{return nodes.flatMap(node=>[node,...flatten(node.children??[])]);}
function mapNodes(nodes:LayoutNode[],map:(node:LayoutNode)=>LayoutNode):LayoutNode[]{return nodes.map(node=>map({...node,children:node.children?mapNodes(node.children,map):undefined}));}
function depth(nodes:LayoutNode[],id:string,current=0):number{for(const node of nodes){if(node.id===id)return current;const child=depth(node.children??[],id,current+1);if(child>=0)return child;}return -1;}
