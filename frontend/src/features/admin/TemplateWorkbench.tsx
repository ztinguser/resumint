"use client";

import Link from "next/link";
import {useEffect,useMemo,useState} from "react";
import {apiFetch,currentUser,type CurrentUser} from "@/features/auth/auth-client";
import {ResumePrintDocument} from "@/resume/ResumePrintDocument";
import {createCandidateSample,formalTemplateCandidates,type FormalTemplateCandidate,type SampleKind} from "@/resume/templates/formal-drafts";

type LayoutNode={id:string;kind:string;label:string;locked:boolean;children?:LayoutNode[]};
type Binding={kind:string;target:string;emptyBehavior:string};
type Config={visualKey:string;layoutTree:{type:string;children:LayoutNode[]};componentCatalog:Array<{kind:string;label:string;allowed:boolean}>;bindings:Binding[];editableParameters:Record<string,{type:string;min?:number;max?:number;step?:number;default:string|number;locked:boolean}>;fallback:{unknownModule:string;unknownField:string};continuation:Record<string,string>};
type Draft={id:string;displayName:string;category:string;tags:string[];listed:boolean;currentVersion:number|null;baseVersion:number|null;schemaVersion:string;config:Config;componentVersions:Record<string,number>;assetManifest:unknown[];revision:number;updatedAt:string};
type Audit={id:string;action:string;details:Record<string,unknown>;createdAt:string};

const actionNames:Record<string,string>={CREATED:"创建模板",DRAFT_SAVED:"保存草稿",VERSION_PUBLISHED:"发布版本",RENAMED:"修改展示名",LISTED:"上架",UNLISTED:"下架"};

export function TemplateWorkbench(){
  const [user,setUser]=useState<CurrentUser|null|undefined>();
  const [candidate,setCandidate]=useState(formalTemplateCandidates[0]);
  const [draft,setDraft]=useState<Draft|null>(null);
  const [config,setConfig]=useState<Config>(candidate.config as Config);
  const [selected,setSelected]=useState("header");
  const [sample,setSample]=useState<SampleKind>("short");
  const [audit,setAudit]=useState<Audit[]>([]);
  const [message,setMessage]=useState("三套候选均为未发布、未上架草稿；等待产品确认视觉、名称与素材授权。");
  useEffect(()=>{currentUser().then(setUser).catch(()=>setUser(null));},[]);
  const sampleDocument=useMemo(()=>createCandidateSample(candidate,sample),[candidate,sample]);
  const nodes=useMemo(()=>flatten(config.layoutTree.children),[config]);
  const selectedNode=nodes.find(node=>node.id===selected)??nodes[0];
  const switchCandidate=(next:FormalTemplateCandidate)=>{setCandidate(next);setDraft(null);setConfig(structuredClone(next.config) as Config);setSelected("header");setAudit([]);setMessage("候选已切换。本地改动尚未显式保存。");};
  const changeNode=(patch:Partial<LayoutNode>)=>setConfig(value=>({...value,layoutTree:{...value.layoutTree,children:mapNodes(value.layoutTree.children,node=>node.id===selected?{...node,...patch}:node)}}));
  const load=async()=>{try{const value=await apiFetch<Draft>(`/api/admin/templates/${candidate.stableId}`);setDraft(value);setConfig(value.config);setAudit(await apiFetch<Audit[]>(`/api/admin/templates/${candidate.stableId}/audit`));setMessage(`已加载草稿 r${value.revision}`);}catch{setMessage("后端尚无该候选草稿，可先检查视觉，再创建待确认草稿。");}};
  const create=async()=>{try{const value=await apiFetch<Draft>("/api/admin/templates",{method:"POST",body:JSON.stringify({id:candidate.stableId,displayName:candidate.recommendedName,category:candidate.category,tags:[candidate.direction,"正式候选"],initialConfig:config})});setDraft(value);setMessage("候选草稿已创建，仍未发布或上架。请再点击“显式保存”锁定完整配置。");}catch(error){setMessage(error instanceof Error?error.message:"创建失败");}};
  const save=async()=>{if(!draft){setMessage("请先创建或加载草稿");return;}try{const value=await apiFetch<Draft>(`/api/admin/templates/${draft.id}/draft`,{method:"PUT",body:JSON.stringify({expectedRevision:draft.revision,schemaVersion:"1.0",config,componentVersions:candidate.componentVersions,assetManifest:candidate.assetManifest})});setDraft(value);setMessage(`已显式保存草稿 r${value.revision}；未发布、未上架。`);setAudit(await apiFetch<Audit[]>(`/api/admin/templates/${draft.id}/audit`));}catch(error){setMessage(error instanceof Error?error.message:"保存失败");}};
  if(user===undefined)return <main className="admin-gate">正在核验管理员身份…</main>;
  if(!user)return <main className="admin-gate"><h1>需要登录</h1><p>模板工作台仅向管理员开放。</p><Link href="/templates">返回模板库</Link></main>;
  if(user.role!=="ADMIN")return <main className="admin-gate"><span className="eyebrow">403 · Access denied</span><h1>无权访问模板工作台</h1><p>普通用户即使直接进入地址，也不能读取或修改模板草稿。</p><Link href="/account">返回个人空间</Link></main>;
  return <main className="admin-workbench">
    <header className="admin-topbar"><Link className="brand" href="/">Resumint</Link><strong>管理员模板工作台</strong><span>身份：{user.maskedPhone} · 管理员</span><button onClick={()=>void load()}>加载草稿</button><button className="primary" onClick={()=>void save()}>显式保存</button></header>
    <div className="approval-banner" role="status">{message}</div>
    <section className="candidate-strip">{formalTemplateCandidates.map(item=><button key={item.stableId} className={candidate.stableId===item.stableId?"active":""} onClick={()=>switchCandidate(item)}><small>{item.direction}</small><strong>{item.recommendedName}</strong><span>{item.stableId}</span></button>)}</section>
    <div className="admin-grid">
      <aside className="admin-left">
        <section><div className="admin-section-title"><h2>受控组件</h2><button onClick={()=>void create()} disabled={!!draft}>创建待确认草稿</button></div>{config.componentCatalog.map(item=><div className="catalog-row" key={item.kind}><span>{item.label}</span><code>{item.kind}</code></div>)}</section>
        <section><h2>布局树</h2><div className="layout-tree">{nodes.map(node=><button key={node.id} className={selected===node.id?"active":""} style={{paddingLeft:`${12+depth(config.layoutTree.children,node.id)*18}px`}} onClick={()=>setSelected(node.id)}><span>{node.locked?"▣":"□"}</span>{node.label}<code>{node.kind}</code></button>)}</div></section>
        <section><h2>字段绑定</h2>{config.bindings.map((binding,index)=><div className="binding-row" key={`${binding.kind}-${index}`}><strong>{binding.kind}</strong><code>{binding.target}</code><span>{binding.emptyBehavior}</span></div>)}</section>
      </aside>
      <section className="admin-preview"><div className="sample-tabs"><span>固定样本</span>{(["short","long","fallback"] as SampleKind[]).map(kind=><button className={sample===kind?"active":""} onClick={()=>setSample(kind)} key={kind}>{kind==="short"?"短内容":kind==="long"?"长内容 / 多页":"自定义字段兜底"}</button>)}</div><div className="candidate-summary"><div><span className="eyebrow">{candidate.direction}候选</span><h1>{candidate.recommendedName}</h1><p>{candidate.rationale}</p></div><dl><dt>稳定 ID</dt><dd>{candidate.stableId}</dd><dt>备选名</dt><dd>{candidate.alternateNames.join(" / ")}</dd><dt>发布状态</dt><dd>待确认 · 未发布 · 未上架</dd></dl></div><div className="admin-preview-scroll"><div className="admin-paper-scale"><ResumePrintDocument initialDocument={sampleDocument}/></div></div></section>
      <aside className="admin-properties"><h2>属性面板</h2>{selectedNode&&<><label>组件名称<input value={selectedNode.label} onChange={event=>changeNode({label:event.target.value})}/></label><label className="check"><input type="checkbox" checked={selectedNode.locked} onChange={event=>changeNode({locked:event.target.checked})}/> 锁定结构属性</label><label>组件类型<input value={selectedNode.kind} disabled/></label></>}<h3>编辑边界</h3>{Object.entries(config.editableParameters).map(([key,value])=><div className="boundary-row" key={key}><strong>{key}</strong><span>{value.locked?"锁定":`${value.min??"—"} – ${value.max??"—"}`}</span><code>默认 {String(value.default)}</code></div>)}<h3>素材与授权</h3><p className="asset-note">{candidate.assetPlan}</p><p className="asset-state">内容寻址素材：{candidate.assetManifest.length?`${candidate.assetManifest.length} 项`:`待确认后生成摘要`}</p><h3>版本与上架</h3><div className="release-actions"><button disabled>发布不可变版本</button><button disabled>上架</button><button disabled={!draft?.listed}>下架</button></div><small className="release-note">视觉、展示名和字体授权确认前，发布与上架保持禁用。</small><h3>审计</h3><ol className="audit-list">{audit.map(event=><li key={event.id}><strong>{actionNames[event.action]??event.action}</strong><time>{new Date(event.createdAt).toLocaleString("zh-CN")}</time></li>)}{!audit.length&&<li>尚无审计事件</li>}</ol></aside>
    </div>
  </main>;
}

function flatten(nodes:LayoutNode[]):LayoutNode[]{return nodes.flatMap(node=>[node,...flatten(node.children??[])]);}
function mapNodes(nodes:LayoutNode[],map:(node:LayoutNode)=>LayoutNode):LayoutNode[]{return nodes.map(node=>map({...node,children:node.children?mapNodes(node.children,map):undefined}));}
function depth(nodes:LayoutNode[],id:string,current=0):number{for(const node of nodes){if(node.id===id)return current;const child=depth(node.children??[],id,current+1);if(child>=0)return child;}return -1;}
