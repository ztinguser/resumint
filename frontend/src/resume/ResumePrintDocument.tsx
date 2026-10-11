"use client";

import { useEffect, useMemo, useState } from "react";
import type { ResumeDocument } from "./model";
import { detectLayoutIssues, paginateResume, type RenderBlock } from "./pagination";
import { assertResumeDocument } from "./validation";
import { GeneralTemplatePage } from "./GeneralTemplatePage";
import { GENERAL_TEMPLATE_ID } from "./templates/general";
import{paginateGeneralResume}from"./general-pagination";
import{themeVariables}from"@/features/editor/direct-canvas";

declare global {
  interface Window {
    renderResume?: (document: ResumeDocument) => void;
    __RESUMINT_READY__?: boolean;
  }
}

function Block({ block }: { block: RenderBlock }) {
  if (block.kind === "section") return <h2 className="section-title">{block.text}</h2>;
  if (block.kind === "item-heading" && block.item) return <div className="item-heading"><div><strong>{block.item.title}</strong>{block.item.subtitle && <span>{block.item.subtitle}</span>}</div>{block.item.date && <time>{block.item.date}</time>}</div>;
  if ((block.kind === "paragraph" || block.kind === "bullet") && block.richText) return <p className={block.kind === "bullet" ? "bullet" : undefined}>{block.richText.spans.map((span,index) => span.link ? <a key={index} href={span.link}>{span.bold ? <strong>{span.text}</strong> : span.text}</a> : span.bold ? <strong key={index}>{span.text}</strong> : <span key={index}>{span.text}</span>)}</p>;
  if (block.kind === "paragraph") return <p>{block.text}</p>;
  if (block.kind === "bullet") return <p className="bullet">{block.text}</p>;
  if (block.kind === "links") return <div className="item-links">{block.links?.map((link) => <a key={link.href} href={link.href}>{link.label}</a>)}</div>;
  return null;
}

export function ResumePrintDocument({ initialDocument }: { initialDocument: ResumeDocument }) {
  const [document, setDocument] = useState(initialDocument);
  const pages = useMemo(() => paginateResume(document), [document]);
  const issues = useMemo(() => detectLayoutIssues(document), [document]);
  const twoColumn = document.layout.kind === "two-column";
  const portraitSize = Math.min(document.profile.portrait?.slotSizeMm ?? 0, 40);

  useEffect(() => {
    window.__RESUMINT_READY__ = false;
    window.renderResume = (next) => {
      assertResumeDocument(next);
      window.__RESUMINT_READY__ = false;
      setDocument(next);
    };
    const frame = requestAnimationFrame(() => { window.__RESUMINT_READY__ = true; });
    return () => cancelAnimationFrame(frame);
  }, [document]);

  const templateClass = `template-${document.template.id.replace(/[^a-z0-9-]/g, "")}`;
  if(document.template.id===GENERAL_TEMPLATE_ID){const generalPages=paginateGeneralResume(document);return <main className={`print-root ${templateClass}`} style={themeVariables(document)} data-issues={issues.join(",")}>{generalPages.map((page,index)=><GeneralTemplatePage document={document} printBlocks={page.main} pageNumber={index+1} key={index}/>)}</main>;}
  return <main className={`print-root ${templateClass}`} style={{ "--accent": document.layout.theme.accentColor, "--font": `'${document.layout.theme.fontFamily}', Arial, 'Microsoft YaHei', sans-serif`, "--body-font-size": `${document.layout.theme.fontSizePt ?? 8.5}pt`, "--body-line-height": document.layout.theme.lineHeight ?? 1.48 } as React.CSSProperties} data-issues={issues.join(",")}>
    {pages.map((page, index) => <article className={`resume-page ${twoColumn ? "two-column" : "single-column"}`} style={{ padding: `${document.layout.page.marginMm}mm` }} data-page={index + 1} key={index}>
      {document.layout.decorations.map((decoration) => <i data-decoration-id={decoration.id} key={decoration.id} className={`decoration ${decoration.type}`} style={{ left: `${decoration.xMm}mm`, top: `${decoration.yMm}mm`, width: `${decoration.widthMm}mm`, height: `${decoration.heightMm}mm`, background: decoration.color, transform: `rotate(${decoration.rotation ?? 0}deg)`, zIndex: decoration.zIndex ?? 0 }} />)}
      <header className={index ? "continuation" : ""}>
        {index === 0 && document.profile.portrait && <div className="portrait-frame" style={{ width: `${portraitSize}mm`, height: `${portraitSize}mm` }}>
          {/* The print renderer must preserve the validated data URI without Next image transformations. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img className="portrait" src={document.profile.portrait.src} alt={document.profile.portrait.alt} style={{ transform: `scale(${document.profile.portrait.cropScale ?? 1})` }} />
        </div>}
        <div><h1>{document.profile.fullName}</h1><p className="headline">{document.profile.headline}</p>{index === 0 && <div className="contacts"><a href={`mailto:${document.profile.email}`}>{document.profile.email}</a>{document.profile.phone && <span>{document.profile.phone}</span>}{document.profile.location && <span>{document.profile.location}</span>}{document.profile.website && <a href={document.profile.website.href}>{document.profile.website.label}</a>}{document.profile.contacts?.map(contact=>contact.href?<a href={contact.href} key={contact.id}>{contact.value}</a>:<span key={contact.id}>{contact.label}：{contact.value}</span>)}</div>}</div>
        {index > 0 && <span className="page-number">第 {index + 1} 页</span>}
      </header>
      <div className="page-body">
        {twoColumn && <aside>{page.side.map((block) => <Block block={block} key={block.key} />)}</aside>}
        <section className="main-column">{page.main.map((block) => <Block block={block} key={block.key} />)}</section>
      </div>
    </article>)}
  </main>;
}
