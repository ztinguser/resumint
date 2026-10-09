export type ResumeLink = { label: string; href: string };

export type ResumeItem = {
  id: string;
  title: string;
  subtitle?: string;
  date?: string;
  summary?: string;
  bullets?: string[];
  links?: ResumeLink[];
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
  profile: {
    fullName: string;
    headline: string;
    email: string;
    phone?: string;
    location?: string;
    website?: ResumeLink;
    portrait?: { src: string; alt: string; slotSizeMm: number; cropScale?: number };
  };
  layout: {
    kind: "single" | "two-column";
    page: { size: "A4"; marginMm: number };
    theme: { fontFamily: string; accentColor: string };
    decorations: Array<{
      id: string;
      type: "rectangle" | "circle" | "header-band" | "divider";
      anchor: "page" | "header";
      xMm: number;
      yMm: number;
      widthMm: number;
      heightMm: number;
      color: string;
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
