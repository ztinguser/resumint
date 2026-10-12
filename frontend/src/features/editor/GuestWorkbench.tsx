"use client";

import { useEffect, useMemo, useReducer, useRef, useState } from "react";
import {useRouter}from"next/navigation";
import { ResumePrintDocument } from "@/resume/ResumePrintDocument";
import { describeLayoutIssues, paginateResume } from "@/resume/pagination";
import type { ElementStyle, ResumeDocument, ResumeItem, ResumeSection } from "@/resume/model";
import { createBenchmarkDocument } from "@/resume/templates/benchmark";
import { cloneDocument, historyReducer, move } from "./history";
import { exportResume } from "@/features/export/export-client";
import { LoginBubble } from "@/features/auth/LoginBubble";
import { ApiError, apiFetch, currentUser, type CurrentUser } from "@/features/auth/auth-client";
import { edited, initialSaveState, loadedSaved, saveFailed, saveStarted, saveSucceeded, type SaveState } from "./save-state";
import {applyTemplateToDocument,type SwitchTemplate} from "./template-switch";
import { GeneralTemplatePage } from "@/resume/GeneralTemplatePage";
import { createGeneralDocument, GENERAL_TEMPLATE_ID } from "@/resume/templates/general";
import {CenteredTemplatePage}from"@/resume/CenteredTemplatePage";
import{CENTERED_TEMPLATE_ID,createCenteredDocument}from"@/resume/templates/centered";
import {duplicateSectionAfter,insertItemAfter,moveById,readRenderedElementStyle,removeById}from"./direct-canvas";
import {hideElement}from"@/resume/element-visibility";
import{paginateGeneralResume}from"@/resume/general-pagination";

const sectionLabels: Record<ResumeSection["type"], string> = { experience: "工作经历", education: "教育经历", project: "项目经历", skills: "技能", custom: "自定义模块" };
const newId = (prefix: string) => `${prefix}-${crypto.randomUUID()}`;

type Draft={id:string;title:string;templateId:string;templateVersion:number;schemaVersion:string;document:ResumeDocument;revision:number;updatedAt:string};
type PublicTemplate=SwitchTemplate;

export function GuestWorkbench({templateId,initialDraftId}:{templateId:string;initialDraftId?:string}) {
  const router=useRouter();
  const initialDirectDocument=templateId===GENERAL_TEMPLATE_ID?createGeneralDocument:templateId===CENTERED_TEMPLATE_ID?createCenteredDocument:null;
  const [history, dispatch] = useReducer(historyReducer, undefined, () => ({ past: [], present: initialDirectDocument&&!initialDraftId?initialDirectDocument():createBenchmarkDocument(), future: [] }));
  const [selectedSectionId, setSelectedSectionId] = useState(initialDirectDocument?"":"work-experience");
  const [selectedElement,setSelectedElement]=useState<{id:string;label:string;renderedStyle?:Required<ElementStyle>}|null>(null);
  const [selectedDecorationId, setSelectedDecorationId] = useState("header-divider");
  const [zoom, setZoom] = useState(1);
  const [tab, setTab] = useState<"style" | "layout" | "decoration">("style");
  const [exportState, setExportState] = useState("导出 PDF");
  const [templateReady, setTemplateReady] = useState(!!initialDirectDocument&&!initialDraftId);
  const [notice, setNotice] = useState<string | null>(null);
  const [dragDocument, setDragDocument] = useState<ResumeDocument | null>(null);
  const [user,setUser]=useState<CurrentUser|null>(null);
  const [loginOpen,setLoginOpen]=useState(false);
  const draftIdRef=useRef<string|undefined>(initialDraftId);
  const serverRevisionRef=useRef<number|null>(null);
  const saveInFlightRef=useRef(false);
  const [documentTitle,setDocumentTitle]=useState("未命名简历");
  const [saveState,setSaveState]=useState<SaveState>(initialSaveState);
  const [conflict,setConflict]=useState<Draft|null>(null);
  const [compareConflict,setCompareConflict]=useState(false);
  const [logoutPrompt,setLogoutPrompt]=useState(false);
  const [switchOpen,setSwitchOpen]=useState(false);
  const [templates,setTemplates]=useState<PublicTemplate[]>([]);
  const [switchTarget,setSwitchTarget]=useState<PublicTemplate|null>(null);
  const [guestWarningOpen,setGuestWarningOpen]=useState(true);
  const dragRef = useRef<{ id: string; startX: number; startY: number; originX: number; originY: number } | null>(null);
  const exportAbortRef = useRef<AbortController | null>(null);
  const document = dragDocument ?? history.present;
  const selected = document.sections.find((section) => section.id === selectedSectionId) ?? document.sections[0];
  const issues = useMemo(() => describeLayoutIssues(document), [document]);
  const pages = useMemo(() => document.template.id===GENERAL_TEMPLATE_ID?paginateGeneralResume(document).length:paginateResume(document).length, [document]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    if(["unsaved","dirty","failed","saving"].includes(saveState.status))window.addEventListener("beforeunload", warn); return () => window.removeEventListener("beforeunload", warn);
  }, [saveState.status]);

  useEffect(()=>{let active=true;currentUser().then(async(value)=>{if(!active)return;setUser(value);
    if(value&&initialDraftId){try{const draft=await apiFetch<Draft>(`/api/resumes/${initialDraftId}`);if(!active)return;dispatch({type:"replace",document:draft.document});setDocumentTitle(draft.title);draftIdRef.current=draft.id;serverRevisionRef.current=draft.revision;setSaveState(loadedSaved());setTemplateReady(true);}catch(error){if(active)setNotice(error instanceof Error?error.message:"草稿加载失败");}}
  }).catch(()=>{});return()=>{active=false;};},[initialDraftId]);

  useEffect(() => {
    if(initialDraftId)return;
    if(templateId===GENERAL_TEMPLATE_ID||templateId===CENTERED_TEMPLATE_ID)return;
    let active=true;
    fetch(`/api/templates/${encodeURIComponent(templateId)}`,{cache:"no-store"}).then(async(response)=>{if(!response.ok)throw new Error("所选模板不可用");return response.json() as Promise<{id:string;version:number}>;}).then((template)=>{if(!active)return;const next=createBenchmarkDocument();next.template={id:template.id,version:template.version};dispatch({type:"commit",document:next});setTemplateReady(true);}).catch((error)=>{if(active)setNotice(error instanceof Error?error.message:"模板加载失败");});
    return()=>{active=false;};
  },[templateId,initialDraftId]);

  const commit = (mutate: (next: ResumeDocument) => void) => { const next = cloneDocument(history.present); mutate(next); dispatch({ type: "commit", document: next }); setSaveState(current=>edited(current)); };
  const refreshRenderedStyle=(id:string)=>window.requestAnimationFrame(()=>{const element=[...window.document.querySelectorAll<HTMLElement>("[data-element-id]")].find(candidate=>candidate.dataset.elementId===id);if(element)setSelectedElement(current=>current?.id===id?{...current,renderedStyle:readRenderedElementStyle(element)}:current);});
  const historyAction=(type:"undo"|"redo")=>{dispatch({type});if(selectedElement)refreshRenderedStyle(selectedElement.id);setSaveState(current=>edited(current));};
  const updateSection = (id: string, mutate: (section: ResumeSection) => void) => commit((next) => { const section = next.sections.find((value) => value.id === id); if (section) mutate(section); });
  const updateItem = (sectionId: string, itemId: string, mutate: (item: ResumeItem) => void) => updateSection(sectionId, (section) => { const item = section.items.find((value) => value.id === itemId); if (item) mutate(item); });

  const onPortrait = (file?: File) => {
    if (!file) return;
    if (!/^image\/(png|jpeg|webp)$/.test(file.type) || file.size > 2 * 1024 * 1024) { setNotice("头像仅支持 PNG、JPEG、WebP，且不得超过 2 MB。"); return; }
    const reader = new FileReader(); reader.onload = () => commit((next) => { next.profile.portrait = { src: String(reader.result), alt: `${next.profile.fullName}头像`, slotSizeMm: next.profile.portrait?.slotSizeMm ?? 32, cropScale: 1 }; }); reader.readAsDataURL(file);
  };

  const beginDecorationDrag = (event: React.PointerEvent, id: string) => {
    const item = history.present.layout.decorations.find((value) => value.id === id); if (!item || item.locked) return;
    event.currentTarget.setPointerCapture(event.pointerId); dragRef.current = { id, startX: event.clientX, startY: event.clientY, originX: item.xMm, originY: item.yMm }; setSelectedDecorationId(id);
  };
  const moveDecoration = (event: React.PointerEvent) => {
    const drag = dragRef.current; if (!drag) return;
    const next = cloneDocument(history.present); const item = next.layout.decorations.find((value) => value.id === drag.id); if (!item) return;
    const pxPerMm = 96 / 25.4 * zoom; item.xMm = Math.max(0, Math.min(210 - item.widthMm, Math.round((drag.originX + (event.clientX - drag.startX) / pxPerMm) / 2) * 2)); item.yMm = Math.max(0, Math.min(297 - item.heightMm, Math.round((drag.originY + (event.clientY - drag.startY) / pxPerMm) / 2) * 2)); setDragDocument(next);
  };
  const endDecorationDrag = () => { if (dragDocument) { dispatch({ type: "commit", document: dragDocument }); setSaveState(current=>edited(current)); } dragRef.current = null; setDragDocument(null); };

  const startExport = async () => {
    setNotice(null); setExportState("正在提交…"); const controller=new AbortController();exportAbortRef.current=controller;
    try { await exportResume(history.present, (status) => setExportState(({ queued: "排队中…", running: "生成中…", succeeded: "正在下载…", failed: "导出失败", cancelled: "已取消", expired: "已过期" } as Record<string,string>)[status.status]),controller.signal); setExportState("已下载 PDF"); }
    catch (error) { setExportState(error instanceof DOMException&&error.name==="AbortError"?"已取消":"重新导出"); if(!(error instanceof DOMException&&error.name==="AbortError"))setNotice(error instanceof Error ? error.message : "导出失败，请重试"); }
    finally{exportAbortRef.current=null;}
  };

  const createDraft=async(snapshot:ResumeDocument,title=documentTitle)=>{
    const saved=await apiFetch<Draft>("/api/resumes",{method:"POST",body:JSON.stringify({title,templateId:snapshot.template.id,templateVersion:snapshot.template.version,schemaVersion:snapshot.schemaVersion,document:snapshot})});
    draftIdRef.current=saved.id;serverRevisionRef.current=saved.revision;setDocumentTitle(saved.title);return saved;
  };
  const saveDraft=async(forceNew=false)=>{
    if(!user){setLoginOpen(true);return false;}if(saveInFlightRef.current)return false;saveInFlightRef.current=true;const snapshot=cloneDocument(history.present);const submittedRevision=saveState.localRevision;
    setNotice(null);setSaveState(current=>saveStarted(current));
    try{
      const currentDraftId=draftIdRef.current;const currentServerRevision=serverRevisionRef.current;
      const saved=forceNew||!currentDraftId?await createDraft(snapshot,forceNew?`${documentTitle}（冲突副本）`:documentTitle):await apiFetch<Draft>(`/api/resumes/${currentDraftId}`,{method:"PUT",body:JSON.stringify({baseRevision:currentServerRevision,title:documentTitle,document:snapshot})});
      draftIdRef.current=saved.id;serverRevisionRef.current=saved.revision;setConflict(null);setCompareConflict(false);
      setSaveState(current=>saveSucceeded({...current,savingRevision:submittedRevision}));return true;
    }catch(error){
      if(error instanceof ApiError&&error.status===401){setUser(null);setLoginOpen(true);setNotice("会话已过期，当前编辑内容仍在；重新登录后请再次手动保存。");}
      else if(error instanceof ApiError&&error.status===409&&draftIdRef.current){try{setConflict(await apiFetch<Draft>(`/api/resumes/${draftIdRef.current}`));}catch{setNotice("检测到版本冲突，但暂时无法读取服务器版本。");}}
      else setNotice(error instanceof Error?error.message:"保存失败，请重试");
      setSaveState(current=>saveFailed(current));return false;
    }finally{saveInFlightRef.current=false;}
  };
  const reloadConflict=()=>{if(!conflict)return;dispatch({type:"replace",document:conflict.document});setDocumentTitle(conflict.title);serverRevisionRef.current=conflict.revision;setSaveState(current=>loadedSaved(current.localRevision));setConflict(null);setCompareConflict(false);};
  const closeLogin=()=>{sessionStorage.setItem("resumint-login-dismissed","1");setLoginOpen(false);};
  const performLogout=async()=>{try{await apiFetch<void>("/api/auth/logout",{method:"POST"});}finally{setUser(null);draftIdRef.current=undefined;serverRevisionRef.current=null;setSaveState(initialSaveState);setLogoutPrompt(false);}};
  const requestLogout=()=>{if(["unsaved","dirty","failed","saving"].includes(saveState.status))setLogoutPrompt(true);else void performLogout();};
  const openTemplateSwitch=async()=>{try{const values=await apiFetch<PublicTemplate[]>("/api/templates");setTemplates(values);const first=values.find(value=>value.id!==document.template.id)??values[0]??null;setSwitchTarget(first);setSwitchOpen(true);}catch(error){setNotice(error instanceof Error?error.message:"模板列表加载失败");}};
  const previewSwitched=useMemo(()=>switchTarget?applyTemplateToDocument(document,switchTarget):null,[document,switchTarget]);
  const confirmTemplateSwitch=()=>{if(!switchTarget)return;dispatch({type:"commit",document:applyTemplateToDocument(history.present,switchTarget)});setSaveState(current=>edited(current));setSwitchOpen(false);setNotice(`已切换到“${switchTarget.name}”，内容已保留；再次手动保存后才更新云端绑定。`);};

  const emailError=/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(document.profile.email)?"":"请输入有效邮箱地址";
  const websiteError=document.profile.website&&!/^https?:\/\//i.test(document.profile.website.href)?"链接需以 http:// 或 https:// 开头":"";
  const exporting=exportState.includes("…");
  const saveLabel=({unsaved:"未保存",saving:"保存中…",saved:"已保存",dirty:"有未保存更改",failed:"保存失败"} as const)[saveState.status];
  const directCanvas=[GENERAL_TEMPLATE_ID,CENTERED_TEMPLATE_ID].includes(templateId)||[GENERAL_TEMPLATE_ID,CENTERED_TEMPLATE_ID].includes(document.template.id);
  const DirectTemplateComponent=document.template.id===CENTERED_TEMPLATE_ID?CenteredTemplatePage:GeneralTemplatePage;
  const addDirectItem=(sectionId:string,afterItemId?:string)=>updateSection(sectionId,section=>{section.items=insertItemAfter(section.items,afterItemId,newId("item"));});
  const deleteDirectItem=(sectionId:string,itemId:string)=>updateSection(sectionId,section=>{section.items=removeById(section.items,itemId);});
  const moveDirectSection=(fromId:string,toId:string)=>commit(next=>{next.sections=moveById(next.sections,fromId,toId);});
  const moveDirectItem=(sectionId:string,fromId:string,toId:string)=>updateSection(sectionId,section=>{section.items=moveById(section.items,fromId,toId);});
  const addDirectContact=()=>{const id=newId("contact");commit(next=>{(next.profile.contacts??=[]).push({id,label:"联系方式",value:"双击填写"});});setSelectedElement({id:`contact.${id}.value`,label:"联系方式"});};
  const deleteDirectContact=(id:string)=>{commit(next=>{next.profile.contacts=removeById(next.profile.contacts??[],id);});setSelectedElement(null);};
  const addDirectSection=(sourceSectionId:string)=>{const sectionId=newId("section");commit(next=>{next.sections=duplicateSectionAfter(next.sections,sourceSectionId,sectionId,kind=>newId(kind),next.elementStyles,next.hiddenElementIds);});setSelectedSectionId(sectionId);setSelectedElement(null);};
  const deleteDirectSection=(sectionId:string)=>{commit(next=>{next.sections=removeById(next.sections,sectionId);});setSelectedSectionId("");setSelectedElement(null);};
  const updateElementStyle=(id:string,key:keyof ElementStyle,value:ElementStyle[keyof ElementStyle])=>commit(next=>{const styles=next.elementStyles??={};styles[id]={...(styles[id]??{}),[key]:value};});
  const resetElementStyle=(id:string,action?:"delete")=>{if(action==="delete"){commit(next=>hideElement(next,id));setSelectedElement(null);return;}commit(next=>{if(next.elementStyles)delete next.elementStyles[id];});refreshRenderedStyle(id);};

  return <main className="workbench">
    <header className="workbench-topbar">
      <a className="brand" href="/templates">Resumint</a><span className="divider" />
      <input aria-label="简历名称" className="document-title title-input" maxLength={120} value={documentTitle} onChange={event=>{setDocumentTitle(event.target.value);setSaveState(current=>edited(current));}}/><span className={`unsaved save-${saveState.status}`}><i /> {saveLabel}</span>
      <div className="topbar-actions"><button aria-label="撤销" disabled={!history.past.length} onClick={() => historyAction("undo")}>↶</button><button aria-label="重做" disabled={!history.future.length} onClick={() => historyAction("redo")}>↷</button><button className="secondary" onClick={()=>void openTemplateSwitch()}>切换模板</button><button className="secondary" disabled={saveState.status==="saving"} onClick={()=>void saveDraft()}>保存</button>{exporting&&<button className="secondary" onClick={()=>exportAbortRef.current?.abort()}>取消导出</button>}<button className="primary" disabled={exporting||!templateReady||!!emailError||!!websiteError} onClick={startExport}>{templateReady?exportState:"加载模板…"}</button><div className="account-anchor"><button aria-label="账号菜单" onClick={()=>user?router.push("/account"):setLoginOpen(value=>!value)}>{user?user.maskedPhone:"登录"}</button><LoginBubble open={loginOpen} onClose={closeLogin} onLogin={value=>{setUser(value);sessionStorage.removeItem("resumint-login-dismissed");}}/></div>{user&&<button className="logout-link" onClick={requestLogout}>退出</button>}</div>
    </header>
    {!user&&guestWarningOpen&&<div className="guest-warning" role="status"><span>游客内容仅保存在当前页面；<button onClick={()=>setLoginOpen(true)}>登录后可手动保存</button></span><button className="guest-warning-close" aria-label="关闭游客提示" title="关闭" onClick={()=>setGuestWarningOpen(false)}>×</button></div>}
    {notice && <div className="toast" role="alert"><span>{notice}</span><button aria-label="关闭提示" onClick={() => setNotice(null)}>×</button></div>}
    {conflict&&<div className="dialog-backdrop"><section className="conflict-dialog" role="dialog" aria-label="版本冲突"><span className="eyebrow">HTTP 409</span><h2>服务器版本已更新</h2><p>本地工作副本仍完整保留。服务器当前为修订 {conflict.revision}，更新于 {new Date(conflict.updatedAt).toLocaleString()}。</p>{compareConflict&&<div className="conflict-compare"><div><strong>本地</strong><pre>{JSON.stringify(history.present.profile,null,2)}</pre></div><div><strong>服务器</strong><pre>{JSON.stringify(conflict.document.profile,null,2)}</pre></div></div>}<div className="dialog-actions"><button onClick={reloadConflict}>重新加载服务器版本</button><button onClick={()=>void saveDraft(true)}>本地另存为新草稿</button><button onClick={()=>setCompareConflict(true)}>查看差异并人工合并</button><button onClick={()=>setConflict(null)}>保留本地，稍后处理</button></div></section></div>}
    {logoutPrompt&&<div className="dialog-backdrop"><section className="conflict-dialog" role="dialog" aria-label="退出确认"><h2>还有未保存更改</h2><p>退出不会偷偷保存。请选择继续编辑、先手动保存，或放弃本次更改后退出。</p><div className="dialog-actions"><button onClick={()=>setLogoutPrompt(false)}>继续编辑</button><button onClick={async()=>{if(await saveDraft())await performLogout();}}>保存后退出</button><button className="danger" onClick={()=>void performLogout()}>放弃并退出</button></div></section></div>}
    {switchOpen&&<div className="dialog-backdrop"><section className="template-switch-dialog" role="dialog" aria-label="切换模板预览"><div className="switch-copy"><span className="eyebrow">先预览，后确认</span><h2>切换模板不会立即保存</h2><label>目标模板<select value={switchTarget?.id??""} onChange={event=>setSwitchTarget(templates.find(value=>value.id===event.target.value)??null)}>{templates.map(value=><option value={value.id} key={value.id}>{value.name} · v{value.version}</option>)}</select></label><div className="switch-impact"><h3>内容影响清单</h3><dl><dt>保留</dt><dd>个人信息、全部模块、重复经历、自定义字段</dd><dt>映射</dt><dd>主栏 / 侧栏按目标模板的栏目规则重新排布</dd><dt>重置</dt><dd>主题色、页边距、栏目和装饰改为目标模板默认值</dd><dt>不兼容</dt><dd>无；未知模块与字段使用通用兜底组件展示</dd></dl></div><p>确认后作为一次操作写入撤销历史；只有再次点击“保存”，云端草稿才绑定新版本。</p><div className="dialog-actions"><button className="primary" onClick={confirmTemplateSwitch}>确认切换</button><button onClick={()=>setSwitchOpen(false)}>取消，保持不变</button></div></div>{previewSwitched&&<div className="switch-preview"><div><ResumePrintDocument key={`${switchTarget?.id}-${switchTarget?.version}`} initialDocument={previewSwitched}/></div></div>}</section></div>}
    <div className={`workbench-grid ${directCanvas?"direct-canvas":""}`}>
      {!directCanvas&&<aside className="editor-panel">
        <div className="panel-heading"><div><span className="eyebrow">内容结构</span><h1>简历内容</h1></div><button onClick={() => { const id=newId("section"); commit((next)=>next.sections.push({id,type:"custom",title:"自定义模块",column:"main",visible:true,items:[]})); setSelectedSectionId(id); }}>＋ 模块</button></div>
        <section className="profile-editor"><h2>个人信息</h2><label>姓名<input aria-label="姓名" maxLength={120} value={document.profile.fullName} onChange={(event)=>commit((next)=>{next.profile.fullName=event.target.value||"未命名";})}/></label><label>职业标题<input aria-label="职业标题" maxLength={120} value={document.profile.headline} onChange={(event)=>commit((next)=>{next.profile.headline=event.target.value||"未填写";})}/></label><div className="two-inputs"><label>邮箱<input aria-label="邮箱" type="email" maxLength={254} aria-invalid={!!emailError} value={document.profile.email} onChange={(event)=>commit((next)=>{next.profile.email=event.target.value;})}/>{emailError&&<small className="field-error">{emailError}</small>}</label><label>电话<input aria-label="电话" maxLength={60} value={document.profile.phone??""} onChange={(event)=>commit((next)=>{next.profile.phone=event.target.value||undefined;})}/></label></div><label>所在地<input aria-label="所在地" maxLength={120} value={document.profile.location??""} onChange={(event)=>commit((next)=>{next.profile.location=event.target.value||undefined;})}/></label><label>个人链接<input aria-label="个人链接" maxLength={2048} aria-invalid={!!websiteError} value={document.profile.website?.href??""} onChange={(event)=>commit((next)=>{next.profile.website=event.target.value?{label:event.target.value,href:event.target.value}:undefined;})}/>{websiteError&&<small className="field-error">{websiteError}</small>}</label></section>
        <div className="module-list" aria-label="简历模块">
          {document.sections.map((section,index) => <button draggable onDragStart={(event)=>event.dataTransfer.setData("text/plain",section.id)} onDragOver={(event)=>event.preventDefault()} onDrop={(event)=>{event.preventDefault();const source=event.dataTransfer.getData("text/plain");const from=document.sections.findIndex((value)=>value.id===source);if(from>=0)commit((next)=>{next.sections=move(next.sections,from,index);});}} className={section.id===selected?.id?"active":""} key={section.id} onClick={()=>setSelectedSectionId(section.id)}><span className="grip">⠿</span><span>{section.title}</span><small>{section.visible ? section.column === "main" ? "主栏" : "侧栏" : "已隐藏"}</small></button>)}
        </div>
        {selected && <section className="section-editor">
          <div className="row-actions"><button disabled={document.sections.indexOf(selected)===0} onClick={()=>commit((next)=>{const at=next.sections.findIndex((value)=>value.id===selected.id);next.sections=move(next.sections,at,at-1);})}>上移</button><button disabled={document.sections.indexOf(selected)===document.sections.length-1} onClick={()=>commit((next)=>{const at=next.sections.findIndex((value)=>value.id===selected.id);next.sections=move(next.sections,at,at+1);})}>下移</button><button onClick={()=>updateSection(selected.id,(section)=>{section.visible=!section.visible;})}>{selected.visible?"隐藏":"恢复"}</button><button className="danger" onClick={()=>commit((next)=>{next.sections=next.sections.filter((value)=>value.id!==selected.id);setSelectedSectionId(next.sections[0]?.id ?? "");})}>删除</button></div>
          <label>模块名称<input maxLength={60} value={selected.title} onChange={(event)=>updateSection(selected.id,(section)=>{section.title=event.target.value || "未命名模块";})} /></label>
          <label>所属栏位<select value={selected.column} onChange={(event)=>updateSection(selected.id,(section)=>{section.column=event.target.value as "main"|"side";})}><option value="main">主栏</option><option value="side">侧栏</option></select></label>
          {selected.items.map((item,itemIndex)=><article className="item-editor" key={item.id}>
            <div className="item-title"><strong>条目 {itemIndex+1}</strong><span><button disabled={!itemIndex} onClick={()=>updateSection(selected.id,(section)=>{section.items=move(section.items,itemIndex,itemIndex-1);})}>↑</button><button disabled={itemIndex===selected.items.length-1} onClick={()=>updateSection(selected.id,(section)=>{section.items=move(section.items,itemIndex,itemIndex+1);})}>↓</button><button aria-label="删除条目" onClick={()=>updateSection(selected.id,(section)=>{section.items.splice(itemIndex,1);})}>×</button></span></div>
            <label>名称<input maxLength={120} value={item.title} onChange={(event)=>updateItem(selected.id,item.id,(value)=>{value.title=event.target.value || "未命名条目";})} /></label>
            <div className="two-inputs"><label>角色 / 专业<input maxLength={120} value={item.subtitle??""} onChange={(event)=>updateItem(selected.id,item.id,(value)=>{value.subtitle=event.target.value || undefined;})} /></label><label>日期<input maxLength={40} value={item.date??""} onChange={(event)=>updateItem(selected.id,item.id,(value)=>{value.date=event.target.value || undefined;})} /></label></div>
            <label>描述<textarea maxLength={4000} value={item.summary??""} onChange={(event)=>updateItem(selected.id,item.id,(value)=>{value.summary=event.target.value || undefined;})} /></label>
            {(item.bullets??[]).map((bullet,index)=><label key={index}>列表项 {index+1}<span className="inline-input"><input maxLength={4000} value={bullet} onChange={(event)=>updateItem(selected.id,item.id,(value)=>{value.bullets![index]=event.target.value;})}/><button onClick={()=>updateItem(selected.id,item.id,(value)=>{value.bullets!.splice(index,1);})}>×</button></span></label>)}
            <div className="row-actions"><button onClick={()=>updateItem(selected.id,item.id,(value)=>{(value.bullets??=[]).push("新的成果描述");})}>＋ 列表</button><button onClick={()=>updateItem(selected.id,item.id,(value)=>{(value.richText??=[]).push({id:newId("rich"),type:"paragraph",spans:[{text:"加粗文本",bold:true},{text:" 与普通文本"}]});})}>B 富文本</button><button onClick={()=>updateItem(selected.id,item.id,(value)=>{(value.richText??=[]).push({id:newId("rich"),type:"paragraph",spans:[{text:"项目链接",link:"https://example.com"}]});})}>↗ 链接</button><button onClick={()=>updateItem(selected.id,item.id,(value)=>{(value.fields??=[]).push({id:newId("field"),label:"自定义字段",type:"short-text",value:"字段内容"});})}>＋ 字段</button></div>
            {(item.fields??[]).map((field,index)=><div className="custom-field" key={field.id}><input aria-label="字段标签" maxLength={60} value={field.label} onChange={(event)=>updateItem(selected.id,item.id,(value)=>{value.fields![index].label=event.target.value||"字段";})}/><select aria-label="字段类型" value={field.type} onChange={(event)=>updateItem(selected.id,item.id,(value)=>{value.fields![index].type=event.target.value as typeof field.type;})}><option value="short-text">短文本</option><option value="long-text">长文本</option><option value="date">日期</option><option value="date-range">日期范围</option><option value="link">链接</option></select><input aria-label="字段值" maxLength={4000} value={field.value} onChange={(event)=>updateItem(selected.id,item.id,(value)=>{value.fields![index].value=event.target.value;})}/><button aria-label="删除字段" onClick={()=>updateItem(selected.id,item.id,(value)=>value.fields!.splice(index,1))}>×</button></div>)}
          </article>)}
          <button className="add-item" onClick={()=>updateSection(selected.id,(section)=>section.items.push({id:newId("item"),title:`新的${sectionLabels[section.type]}`}))}>＋ 添加条目</button>
        </section>}
      </aside>}
      <section className="preview-panel">
        <div className="preview-toolbar"><span>导出约 {pages} 页</span><button aria-label="缩小画布" onClick={()=>setZoom(Math.max(.5,Number((zoom-.1).toFixed(1))))}>−</button><button className="zoom-value" aria-label="恢复 100%" onClick={()=>setZoom(1)}>{Math.round(zoom*100)}%</button><button aria-label="放大画布" onClick={()=>setZoom(Math.min(1.4,Number((zoom+.1).toFixed(1))))}>＋</button></div>
        <div className="preview-scroll"><div className={`preview-stage ${directCanvas?"direct-stage":""}`} style={directCanvas?{width:`${210*3.7795*zoom}px`}:{width:`${210*3.7795*zoom}px`,height:`${297*3.7795*pages*zoom+Math.max(0,pages-1)*18*zoom}px`}}><div className="preview-scale" style={directCanvas?{zoom}:{transform:`scale(${zoom})`}}>{directCanvas?<DirectTemplateComponent document={document} editable selectedSectionId={selectedSectionId} selectedElementId={selectedElement?.id} onClearSelection={()=>{setSelectedSectionId("");setSelectedElement(null);}} onSelectSection={id=>{setSelectedSectionId(id);setSelectedElement(null);}} onSelectElement={(id,label,renderedStyle)=>{setSelectedSectionId("");setSelectedElement({id,label,renderedStyle});}} onProfileChange={(key,value)=>commit(next=>{next.profile[key]=value;})} onWebsiteChange={value=>commit(next=>{next.profile.website={label:value,href:next.profile.website?.href??"https://example.com"};})} onContactChange={(id,key,value)=>commit(next=>{const contact=next.profile.contacts?.find(item=>item.id===id);if(contact)contact[key]=value;})} onAddContact={addDirectContact} onDeleteContact={deleteDirectContact} onSectionChange={(sectionId,_key,value)=>updateSection(sectionId,section=>{section.title=value;})} onItemChange={(sectionId,itemId,key,value)=>updateItem(sectionId,itemId,item=>{item[key]=value;})} onAddItem={addDirectItem} onDeleteItem={deleteDirectItem} onAddSection={addDirectSection} onDeleteSection={deleteDirectSection} onMoveSection={moveDirectSection} onMoveItem={moveDirectItem}/>:<ResumePrintDocument initialDocument={document} key={JSON.stringify(document)}/>} {!directCanvas&&<>{dragDocument&&<><i className="alignment-guide vertical"/><i className="alignment-guide horizontal"/></>}{document.layout.decorations.map((item)=><button aria-label={`拖动装饰 ${item.id}`} className={`decoration-handle ${selectedDecorationId===item.id?"selected":""}`} disabled={item.locked} key={item.id} style={{left:`${item.xMm}mm`,top:`${item.yMm}mm`,width:`${Math.max(item.widthMm,5)}mm`,height:`${Math.max(item.heightMm,5)}mm`}} onPointerDown={(event)=>beginDecorationDrag(event,item.id)} onPointerMove={moveDecoration} onPointerUp={endDecorationDrag}/>)}</>}</div></div></div>
        <div className="layout-status"><strong>{pages} 页 A4</strong><span>{issues.length ? `${issues.length} 个排版问题` : "未发现排版问题"}</span>{issues.map((issue)=><button key={`${issue.code}-${issue.objectId}`} onClick={()=>setSelectedDecorationId(issue.objectId)}>{issue.code} · 第 {issue.page} 页 · {issue.objectId}</button>)}</div>
      </section>
      <aside className={`property-panel ${directCanvas?"direct-properties":""}`}>
        {directCanvas?<div className="property-content"><span className="eyebrow">直接编辑</span><h2>{selectedElement?selectedElement.label:selected?.title??"页面样式"}</h2><p className="help">单击文字调整样式，双击修改内容；悬停查看 ＋ / ×，可新增或删除联系方式、条目和完整模块。</p>{selectedElement?<ElementStylePanel document={document} elementId={selectedElement.id} renderedStyle={selectedElement.renderedStyle??null} update={updateElementStyle} reset={resetElementStyle}/>:<><h2>页面样式</h2><label>文字颜色<input type="color" value={document.layout.theme.accentColor} onInput={(event)=>commit(next=>{next.layout.theme.accentColor=event.currentTarget.value;})}/></label><label>正文字号 <output>{document.layout.theme.fontSizePt??8.5} pt</output><input type="range" min="8" max="11" step="0.5" value={document.layout.theme.fontSizePt??8.5} onInput={(event)=>commit(next=>{next.layout.theme.fontSizePt=Number(event.currentTarget.value);})}/></label><h2>头像</h2><label className="file-label">替换头像<input type="file" accept="image/png,image/jpeg,image/webp" onChange={(event)=>onPortrait(event.target.files?.[0])}/></label></>}</div>:<>
        <div className="tabs"><button className={tab==="style"?"active":""} onClick={()=>setTab("style")}>样式</button><button className={tab==="layout"?"active":""} onClick={()=>setTab("layout")}>布局</button><button className={tab==="decoration"?"active":""} onClick={()=>setTab("decoration")}>装饰</button></div>
        {tab==="style"&&<div className="property-content"><h2>主题样式</h2><label>主题色<input type="color" value={document.layout.theme.accentColor} onChange={(event)=>commit((next)=>{next.layout.theme.accentColor=event.target.value;})}/></label><label>中文字体<select value={document.layout.theme.fontFamily} onChange={(event)=>commit((next)=>{next.layout.theme.fontFamily=event.target.value;})}><option>Arial</option><option>Microsoft YaHei</option><option>SimSun</option></select></label><label>正文字号 <output>{document.layout.theme.fontSizePt??8.5} pt</output><input type="range" min="8" max="13" step="0.5" value={document.layout.theme.fontSizePt??8.5} onChange={(event)=>commit((next)=>{next.layout.theme.fontSizePt=Number(event.target.value);})}/></label><label>正文行距 <output>{document.layout.theme.lineHeight??1.48}</output><input type="range" min="1.2" max="1.8" step="0.02" value={document.layout.theme.lineHeight??1.48} onChange={(event)=>commit((next)=>{next.layout.theme.lineHeight=Number(event.target.value);})}/></label><h2>头像图片</h2><label className="file-label">替换头像<input type="file" accept="image/png,image/jpeg,image/webp" onChange={(event)=>onPortrait(event.target.files?.[0])}/></label>{document.profile.portrait&&<><label>裁剪缩放 <output>{document.profile.portrait.cropScale?.toFixed(1)}×</output><input type="range" min="1" max="4" step="0.1" value={document.profile.portrait.cropScale??1} onChange={(event)=>commit((next)=>{next.profile.portrait!.cropScale=Number(event.target.value);})}/></label><button onClick={()=>commit((next)=>{delete next.profile.portrait;})}>移除头像</button></>}</div>}
        {tab==="layout"&&<div className="property-content"><h2>页面与栏目</h2><label>栏目<select value={document.layout.kind} onChange={(event)=>commit((next)=>{next.layout.kind=event.target.value as "single"|"two-column";})}><option value="single">单栏</option><option value="two-column">双栏</option></select></label><label>页边距 <output>{document.layout.page.marginMm} mm</output><input type="range" min="10" max="25" step="1" value={document.layout.page.marginMm} onChange={(event)=>commit((next)=>{next.layout.page.marginMm=Number(event.target.value);})}/></label><p className="help">A4（210 × 297 mm）· 自动分页续排</p></div>}
        {tab==="decoration"&&<DecorationPanel document={document} selectedId={selectedDecorationId} select={setSelectedDecorationId} commit={commit}/>}</>}
      </aside>
    </div>
  </main>;
}

function ElementStylePanel({document,elementId,renderedStyle,update,reset}:{document:ResumeDocument;elementId:string;renderedStyle:Required<ElementStyle>|null;update:(id:string,key:keyof ElementStyle,value:ElementStyle[keyof ElementStyle])=>void;reset:(id:string,action?:"delete")=>void}){
  const style=document.elementStyles?.[elementId]??{};
  const fontFamily=style.fontFamily??renderedStyle?.fontFamily??document.layout.theme.fontFamily;
  const fontSize=style.fontSizePt??renderedStyle?.fontSizePt??document.layout.theme.fontSizePt??9;
  const color=style.color??renderedStyle?.color??document.layout.theme.accentColor;
  const lineHeight=style.lineHeight??renderedStyle?.lineHeight??document.layout.theme.lineHeight??1.48;
  const spacing=style.spacingMm??renderedStyle?.spacingMm??0;
  const deleteLabel=elementId.startsWith("section.")?"分区标题":elementId.endsWith(".title")?"条目名称":elementId.endsWith(".subtitle")?"角色或专业":elementId.endsWith(".date")?"日期":elementId.endsWith(".summary")?"描述":"文字";
  return <div className="element-style-panel"><div className="property-title"><h2>元素样式</h2><span><button onClick={()=>reset(elementId)}>恢复默认</button><button className="danger" aria-label={`删除元素 ${deleteLabel}`} onClick={()=>reset(elementId,"delete")}>删除</button></span></div><label>字体系列<select aria-label="元素字体系列" value={fontFamily} onChange={event=>update(elementId,"fontFamily",event.target.value)}><option>Microsoft YaHei</option><option>Arial</option><option>SimSun</option></select></label><label>文字颜色<input aria-label="元素文字颜色" type="color" value={color} onInput={event=>update(elementId,"color",event.currentTarget.value)}/></label><label>字号 <output>{fontSize} pt</output><input aria-label="元素字号" type="range" min="6" max="36" step="0.5" value={fontSize} onInput={event=>update(elementId,"fontSizePt",Number(event.currentTarget.value))}/></label><label>行高 <output>{lineHeight}</output><input aria-label="元素行高" type="range" min="1" max="2.4" step="0.05" value={lineHeight} onInput={event=>update(elementId,"lineHeight",Number(event.currentTarget.value))}/></label><label>下方间距 <output>{spacing} mm</output><input aria-label="元素下方间距" type="range" min="0" max="20" step="0.5" value={spacing} onInput={event=>update(elementId,"spacingMm",Number(event.currentTarget.value))}/></label></div>;
}

function DecorationPanel({document,selectedId,select,commit}:{document:ResumeDocument;selectedId:string;select:(id:string)=>void;commit:(mutate:(next:ResumeDocument)=>void)=>void}) {
  const selected=document.layout.decorations.find((item)=>item.id===selectedId);
  const update=(mutate:(item:ResumeDocument["layout"]["decorations"][number])=>void)=>commit((next)=>{const item=next.layout.decorations.find((value)=>value.id===selectedId);if(item)mutate(item);});
  return <div className="property-content"><div className="property-title"><h2>装饰组件</h2><button onClick={()=>{const id=newId("decoration");commit((next)=>next.layout.decorations.push({id,type:"rectangle",anchor:"page",xMm:16,yMm:70,widthMm:24,heightMm:8,color:"#d8e5e2",rotation:0,zIndex:1,locked:false}));select(id);}}>＋ 添加</button></div><div className="decoration-list">{document.layout.decorations.map((item)=><button className={item.id===selectedId?"active":""} key={item.id} onClick={()=>select(item.id)}>{item.type}<small>{item.locked?"已锁定":"可编辑"}</small></button>)}</div>{selected&&<><label>颜色<input type="color" value={selected.color} onChange={(event)=>update((item)=>{item.color=event.target.value;})}/></label><label>宽度 <output>{selected.widthMm} mm</output><input type="range" min="1" max="210" step="1" value={selected.widthMm} onChange={(event)=>update((item)=>{item.widthMm=Number(event.target.value);})}/></label><label>高度 <output>{selected.heightMm} mm</output><input type="range" min="0.4" max="100" step="0.5" value={selected.heightMm} onChange={(event)=>update((item)=>{item.heightMm=Number(event.target.value);})}/></label><label>旋转 <output>{selected.rotation??0}°</output><input type="range" min="-180" max="180" step="1" value={selected.rotation??0} onChange={(event)=>update((item)=>{item.rotation=Number(event.target.value);})}/></label><label>层级 <output>{selected.zIndex??0}</output><input type="range" min="0" max="20" step="1" value={selected.zIndex??0} onChange={(event)=>update((item)=>{item.zIndex=Number(event.target.value);})}/></label><div className="row-actions"><button onClick={()=>update((item)=>{item.locked=!item.locked;})}>{selected.locked?"解锁":"锁定"}</button><button className="danger" onClick={()=>commit((next)=>{next.layout.decorations=next.layout.decorations.filter((item)=>item.id!==selectedId);})}>删除</button></div><p className="help">可直接在预览中拖动；按 2 mm 吸附，缩放时自动换算文档坐标。</p></>}</div>;
}
