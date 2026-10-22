import type { LayoutIssueCode, ResumeDocument, ResumeItem, ResumeSection, RichTextBlock } from "./model";
import { isElementVisible } from "./element-visibility";

export type RenderBlock = {
  key: string;
  kind: "section" | "item-heading" | "paragraph" | "bullet" | "links";
  text?: string;
  item?: ResumeItem;
  links?: ResumeItem["links"];
  richText?: RichTextBlock;
  units: number;
};

export type RenderPage = { main: RenderBlock[]; side: RenderBlock[] };

const splitText = (text: string, limit: number): string[] => {
  const pieces: string[] = [];
  let rest = text.trim();
  while (rest.length > limit) {
    const candidates = [rest.lastIndexOf("。", limit), rest.lastIndexOf("；", limit), rest.lastIndexOf("，", limit), rest.lastIndexOf(" ", limit)];
    const cut = Math.max(...candidates, Math.floor(limit * 0.65));
    pieces.push(rest.slice(0, cut + 1).trim());
    rest = rest.slice(cut + 1).trim();
  }
  if (rest) pieces.push(rest);
  return pieces;
};

const textUnits = (text: string, charsPerLine: number) => Math.max(2, Math.ceil(text.length / charsPerLine) * 1.45 + 0.8);

function sectionBlocks(document:ResumeDocument,section: ResumeSection, charsPerLine: number): RenderBlock[] {
  const blocks: RenderBlock[] = isElementVisible(document,`section.${section.id}.title`)?[{ key: `${section.id}-title`, kind: "section", text: section.title, units: 4 }]:[];
  for (const item of section.items) {
    const visibleHeading = (["title","subtitle","date"] as const)
      .filter(key=>isElementVisible(document,`item.${item.id}.${key}`)&&!!item[key])
      .map(key=>item[key] as string);
    if(visibleHeading.length)blocks.push({
      key: `${item.id}-heading`,
      kind: "item-heading",
      item,
      units: Math.max(4,textUnits(visibleHeading.join(" "),charsPerLine)),
    });
    for (const [index, text] of (isElementVisible(document,`item.${item.id}.summary`)?splitText(item.summary ?? "", charsPerLine * 5):[]).entries()) {
      blocks.push({ key: `${item.id}-summary-${index}`, kind: "paragraph", text, units: textUnits(text, charsPerLine) });
    }
    for (const [index, bullet] of (item.bullets ?? []).entries()) {
      for (const [part, text] of splitText(bullet, charsPerLine * 4).entries()) {
        blocks.push({ key: `${item.id}-bullet-${index}-${part}`, kind: "bullet", text, units: textUnits(text, charsPerLine) });
      }
    }
    if (item.links?.length) blocks.push({ key: `${item.id}-links`, kind: "links", links: item.links, units: 3.5 });
    for (const field of item.fields ?? []) {
      const text = `${field.label}：${field.value}`;
      blocks.push({ key: `${item.id}-${field.id}`, kind: "paragraph", text, units: textUnits(text, charsPerLine) });
    }
    for (const block of item.richText ?? []) {
      const text = block.spans.map((span) => span.text).join("");
      blocks.push({ key: `${item.id}-${block.id}`, kind: block.type, text, richText: block, units: textUnits(text, charsPerLine) });
    }
  }
  return blocks;
}

function pack(blocks: RenderBlock[], firstCapacity: number, continuationCapacity: number,keepItemsTogether=false): RenderBlock[][] {
  if (!blocks.length) return [[]];
  const pages: RenderBlock[][] = [[]];
  let used = 0;
  for (let index=0;index<blocks.length;index+=1) {
    const block=blocks[index];
    let capacity = pages.length === 1 ? firstCapacity : continuationCapacity;
    if(keepItemsTogether&&block.kind==="item-heading"&&pages.at(-1)!.length){
      let itemUnits=block.units;
      for(let next=index+1;next<blocks.length&&!(["section","item-heading"] as RenderBlock["kind"][]).includes(blocks[next].kind);next+=1)itemUnits+=blocks[next].units;
      if(itemUnits<=continuationCapacity&&used+itemUnits>capacity){
        pages.push([]);
        used=0;
        capacity=continuationCapacity;
      }
    }
    if (pages.at(-1)!.length && used + block.units > capacity) {
      pages.push([]);
      used = 0;
    }
    pages.at(-1)!.push(block);
    used += block.units;
  }
  return pages;
}

export function paginateResume(document: ResumeDocument, capacities?:{firstMain?:number;continuationMain?:number;firstSide?:number;continuationSide?:number;keepItemsTogether?:boolean}): RenderPage[] {
  const visible = document.sections.filter((section) => section.visible);
  const twoColumn = document.layout.kind === "two-column";
  const main = visible.filter((section) => section.column === "main").flatMap((section) => sectionBlocks(document,section, twoColumn ? 34 : 78));
  const side = visible.filter((section) => section.column === "side").flatMap((section) => sectionBlocks(document,section, 13));
  const mainPages = pack(main, capacities?.firstMain??(twoColumn ? 47 : 49), capacities?.continuationMain??(twoColumn ? 61 : 64),capacities?.keepItemsTogether);
  const sidePages = twoColumn ? pack(side, capacities?.firstSide??47, capacities?.continuationSide??61,capacities?.keepItemsTogether) : [[]];
  const count = Math.max(mainPages.length, sidePages.length);
  return Array.from({ length: count }, (_, index) => ({ main: mainPages[index] ?? [], side: sidePages[index] ?? [] }));
}

export function detectLayoutIssues(document: ResumeDocument): LayoutIssueCode[] {
  const issues: LayoutIssueCode[] = [];
  if (document.layout.theme.fontFamily.startsWith("Missing ")) issues.push("FONT_FALLBACK_USED");
  if ((document.profile.portrait?.slotSizeMm ?? 0) > 40) issues.push("IMAGE_BOUNDS_CLAMPED");
  return issues;
}

export type LayoutIssue = { code: "TEXT_TRUNCATED" | "ELEMENT_TOO_LARGE" | "ELEMENT_OUT_OF_BOUNDS" | LayoutIssueCode; page: number; objectId: string };
export function describeLayoutIssues(document: ResumeDocument): LayoutIssue[] {
  const issues: LayoutIssue[] = detectLayoutIssues(document).map((code) => ({ code, page: 1, objectId: code === "IMAGE_BOUNDS_CLAMPED" ? "profile-portrait" : "document-theme" }));
  document.layout.decorations.forEach((item) => {
    if (item.xMm + item.widthMm > 210 || item.yMm + item.heightMm > 297) issues.push({ code: "ELEMENT_OUT_OF_BOUNDS", page: 1, objectId: item.id });
    if (item.widthMm > 190 || item.heightMm > 277) issues.push({ code: "ELEMENT_TOO_LARGE", page: 1, objectId: item.id });
  });
  return issues;
}
