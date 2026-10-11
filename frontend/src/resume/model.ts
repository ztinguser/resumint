export type ResumeLink = { label: string; href: string };
export type ResumeField = { id: string; label: string; type: "short-text" | "long-text" | "date" | "date-range" | "link"; value: string };
export type RichTextBlock = { id: string; type: "paragraph" | "bullet"; spans: Array<{ text: string; bold?: boolean; link?: string }> };
export type ElementStyle = { fontFamily?: string; fontSizePt?: number; color?: string; lineHeight?: number; spacingMm?: number };

export type ResumeItem = {
  id: string;
  title: string;
  subtitle?: string;
  date?: string;
  summary?: string;
  bullets?: string[];
  links?: ResumeLink[];
  fields?: ResumeField[];
  richText?: RichTextBlock[];
};

export type ResumeSection = {
  id: string;
  type: "experience" | "education" | "project" | "skills" | "custom";
  title: string;
  column: "main" | "side";
  visible: boolean;
  items: ResumeItem[];
};

export type ResumeDocument = {
  schemaVersion: "1.0";
  documentId: string;
  template: { id: string; version: number };
  locale: string;
  elementStyles?: Record<string, ElementStyle>;
  profile: {
    fullName: string;
    headline: string;
    contactTitle?: string;
    email: string;
    phone?: string;
    location?: string;
    website?: ResumeLink;
    contacts?: Array<{ id: string; label: string; value: string; href?: string }>;
    portrait?: { src: string; alt: string; slotSizeMm: number; cropScale?: number };
  };
  layout: {
    kind: "single" | "two-column";
    page: { size: "A4"; marginMm: number };
    theme: { fontFamily: string; accentColor: string; fontSizePt?: number; lineHeight?: number };
    decorations: Array<{
      id: string;
      type: "rectangle" | "circle" | "header-band" | "divider";
      anchor: "page" | "header";
      xMm: number;
      yMm: number;
      widthMm: number;
      heightMm: number;
      color: string;
      rotation?: number;
      zIndex?: number;
      locked?: boolean;
    }>;
  };
  sections: ResumeSection[];
};

export type LayoutIssueCode = "FONT_FALLBACK_USED" | "IMAGE_BOUNDS_CLAMPED";

export type BenchmarkFixture = {
  id: string;
  document: ResumeDocument;
  expected: {
    pages: number;
    text: string[];
    links: string[];
    issues: LayoutIssueCode[];
    screenshotThreshold: number;
  };
};
