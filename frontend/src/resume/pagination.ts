import type { LayoutIssueCode, ResumeDocument, ResumeItem, ResumeSection } from "./model";

export type RenderBlock = {
  key: string;
  kind: "section" | "item-heading" | "paragraph" | "bullet" | "links";
  text?: string;
  item?: ResumeItem;
  links?: ResumeItem["links"];
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

function sectionBlocks(section: ResumeSection, charsPerLine: number): RenderBlock[] {
  const blocks: RenderBlock[] = [{ key: `${section.id}-title`, kind: "section", text: section.title, units: 4 }];
  for (const item of section.items) {
    blocks.push({ key: `${item.id}-heading`, kind: "item-heading", item, units: 4 });
    for (const [index, text] of splitText(item.summary ?? "", charsPerLine * 5).entries()) {
      blocks.push({ key: `${item.id}-summary-${index}`, kind: "paragraph", text, units: textUnits(text, charsPerLine) });
    }
    for (const [index, bullet] of (item.bullets ?? []).entries()) {
      for (const [part, text] of splitText(bullet, charsPerLine * 4).entries()) {
        blocks.push({ key: `${item.id}-bullet-${index}-${part}`, kind: "bullet", text, units: textUnits(text, charsPerLine) });
      }
    }
    if (item.links?.length) blocks.push({ key: `${item.id}-links`, kind: "links", links: item.links, units: 3.5 });
  }
  return blocks;
}

function pack(blocks: RenderBlock[], firstCapacity: number, continuationCapacity: number): RenderBlock[][] {
  if (!blocks.length) return [[]];
  const pages: RenderBlock[][] = [[]];
  let used = 0;
  for (const block of blocks) {
    const capacity = pages.length === 1 ? firstCapacity : continuationCapacity;
    if (pages.at(-1)!.length && used + block.units > capacity) {
      pages.push([]);
      used = 0;
    }
    pages.at(-1)!.push(block);
    used += block.units;
  }
  return pages;
}

export function paginateResume(document: ResumeDocument): RenderPage[] {
  const visible = document.sections.filter((section) => section.visible);
  const twoColumn = document.layout.kind === "two-column";
  const main = visible.filter((section) => section.column === "main").flatMap((section) => sectionBlocks(section, twoColumn ? 48 : 78));
  const side = visible.filter((section) => section.column === "side").flatMap((section) => sectionBlocks(section, 30));
  const mainPages = pack(main, twoColumn ? 47 : 49, twoColumn ? 61 : 64);
  const sidePages = twoColumn ? pack(side, 47, 61) : [[]];
  const count = Math.max(mainPages.length, sidePages.length);
  return Array.from({ length: count }, (_, index) => ({ main: mainPages[index] ?? [], side: sidePages[index] ?? [] }));
}

export function detectLayoutIssues(document: ResumeDocument): LayoutIssueCode[] {
  const issues: LayoutIssueCode[] = [];
  if (document.layout.theme.fontFamily.startsWith("Missing ")) issues.push("FONT_FALLBACK_USED");
  if ((document.profile.portrait?.slotSizeMm ?? 0) > 40) issues.push("IMAGE_BOUNDS_CLAMPED");
  return issues;
}
