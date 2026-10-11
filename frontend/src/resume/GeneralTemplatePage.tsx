"use client";

import { useEffect, useRef, useState } from "react";
import type { ResumeDocument, ResumeItem, ResumeSection } from "./model";

type Props = {
  document: ResumeDocument;
  editable?: boolean;
  selectedSectionId?: string;
  onSelectSection?: (id: string) => void;
  onProfileChange?: (key: "fullName" | "headline" | "email" | "phone" | "location", value: string) => void;
  onWebsiteChange?: (value: string) => void;
  onSectionChange?: (sectionId: string, key: "title", value: string) => void;
  onItemChange?: (sectionId: string, itemId: string, key: "title" | "subtitle" | "date" | "summary", value: string) => void;
  onAddItem?: (sectionId: string) => void;
  onMoveSection?: (fromId: string, toId: string) => void;
};

function EditableText({ value, onCommit, className, multiline = false, label }: { value: string; onCommit?: (value: string) => void; className?: string; multiline?: boolean; label: string }) {
  const [editing, setEditing] = useState(false);
  const ref = useRef<HTMLElement>(null);
  useEffect(() => { if (editing) { ref.current?.focus(); document.execCommand("selectAll", false); } }, [editing]);
  const finish = () => { const next = ref.current?.textContent?.trim() || value; setEditing(false); if (next !== value) onCommit?.(next); };
  const Tag = multiline ? "div" : "span";
  return <Tag
    ref={ref as never}
    className={`${className ?? ""} ${onCommit ? "direct-editable" : ""} ${editing ? "is-editing" : ""}`}
    contentEditable={editing}
    suppressContentEditableWarning
    role={onCommit ? "textbox" : undefined}
    aria-label={onCommit ? `${label}，双击编辑` : undefined}
    title={onCommit ? "双击编辑" : undefined}
    onDoubleClick={() => onCommit && setEditing(true)}
    onBlur={finish}
    onKeyDown={(event) => {
      if (event.key === "Escape") { if(ref.current)ref.current.textContent=value; setEditing(false); }
      if (!multiline && event.key === "Enter") { event.preventDefault(); finish(); }
    }}
  >{value}</Tag>;
}

function ContactLine({ children }: { children: React.ReactNode }) { return <li>{children}</li>; }

function Section({ section, editable, selected, onSelect, onChange, onItemChange, onAddItem, onDragStart, onDrop }: {
  section: ResumeSection; editable?: boolean; selected?: boolean; onSelect?: () => void;
  onChange?: (value: string) => void; onItemChange?: (item: ResumeItem, key: "title" | "subtitle" | "date" | "summary", value: string) => void;
  onAddItem?: () => void; onDragStart?: () => void; onDrop?: () => void;
}) {
  return <section className={`general-section ${selected ? "is-selected" : ""}`} onClick={onSelect} draggable={editable} onDragStart={onDragStart} onDragOver={(event) => editable && event.preventDefault()} onDrop={(event) => { event.preventDefault(); onDrop?.(); }}>
    {editable && <span className="canvas-grip" aria-hidden>⠿</span>}
    <EditableText className="general-section-title" value={section.title} onCommit={onChange} label="分区标题" />
    <div className="general-section-items">
      {section.items.map((item) => <article className="general-entry" key={item.id}>
        {(section.type === "experience" || section.type === "education") && <div className="general-entry-heading"><div><EditableText className="general-entry-title" value={item.title} onCommit={value => onItemChange?.(item, "title", value)} label="条目名称" />{item.subtitle && <EditableText className="general-entry-subtitle" value={item.subtitle} onCommit={value => onItemChange?.(item, "subtitle", value)} label="角色或专业" />}</div>{item.date && <EditableText className="general-entry-date" value={item.date} onCommit={value => onItemChange?.(item, "date", value)} label="日期" />}</div>}
        {item.summary && <EditableText className="general-entry-summary" multiline value={item.summary} onCommit={value => onItemChange?.(item, "summary", value)} label="描述" />}
      </article>)}
    </div>
    {editable && <button className="canvas-add" type="button" aria-label={`在${section.title}中新增一项`} title="新增同类内容" onClick={(event) => { event.stopPropagation(); onAddItem?.(); }}>＋</button>}
  </section>;
}

export function GeneralTemplatePage(props: Props) {
  const { document, editable, selectedSectionId } = props;
  const [dragged, setDragged] = useState<string | null>(null);
  const profile = document.profile;
  const intro = document.sections.find(section => section.id === "general-profile" && section.visible);
  const body = document.sections.filter(section => section.id !== "general-profile" && section.visible);
  const profileItem = intro?.items[0];
  return <article className={`resume-page general-template-page ${editable ? "is-editable" : ""}`}>
    <header className="general-header">
      {profile.portrait && <div className="general-avatar">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={profile.portrait.src} alt={profile.portrait.alt} />
      </div>}
      <div className="general-identity"><EditableText className="general-headline" value={profile.headline} onCommit={value => props.onProfileChange?.("headline", value)} label="职业标题" /><EditableText className="general-name" value={profile.fullName} onCommit={value => props.onProfileChange?.("fullName", value)} label="姓名" /></div>
    </header>
    <section className="general-overview">
      <div className="general-profile-copy"><h2>个人简介</h2>{profileItem?.summary && <EditableText multiline value={profileItem.summary} onCommit={value => props.onItemChange?.(intro!.id, profileItem.id, "summary", value)} label="个人简介" />}</div>
      <div className="general-contact-copy"><h2>联系方式</h2><ul>
        {profile.location && <ContactLine><EditableText value={profile.location} onCommit={value => props.onProfileChange?.("location", value)} label="所在地" /></ContactLine>}
        <ContactLine><EditableText value={profile.email} onCommit={value => props.onProfileChange?.("email", value)} label="邮箱" /></ContactLine>
        {profile.phone && <ContactLine><EditableText value={profile.phone} onCommit={value => props.onProfileChange?.("phone", value)} label="电话" /></ContactLine>}
        {profile.website && <ContactLine><EditableText value={profile.website.label} onCommit={props.onWebsiteChange} label="个人链接" /></ContactLine>}
      </ul></div>
    </section>
    <div className="general-body">
      {body.map(section => <Section key={section.id} section={section} editable={editable} selected={section.id === selectedSectionId} onSelect={() => props.onSelectSection?.(section.id)} onChange={value => props.onSectionChange?.(section.id, "title", value)} onItemChange={(item, key, value) => props.onItemChange?.(section.id, item.id, key, value)} onAddItem={() => props.onAddItem?.(section.id)} onDragStart={() => setDragged(section.id)} onDrop={() => { if (dragged && dragged !== section.id) props.onMoveSection?.(dragged, section.id); setDragged(null); }} />)}
    </div>
  </article>;
}
