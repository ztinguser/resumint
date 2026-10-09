import type { BenchmarkFixture, ResumeDocument, ResumeItem, ResumeSection } from "../../src/resume/model";

const portrait = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxMDAgMTAwIj48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgZmlsbD0iI2Q4ZTVlMiIvPjxjaXJjbGUgY3g9IjUwIiBjeT0iMzgiIHI9IjE4IiBmaWxsPSIjMjQ0YjQ1Ii8+PHBhdGggZD0iTTIwIDEwMGMwLTI1IDEzLTM4IDMwLTM4czMwIDEzIDMwIDM4IiBmaWxsPSIjMjQ0YjQ1Ii8+PC9zdmc+";

const base = (id: string, kind: "single" | "two-column" = "single"): ResumeDocument => ({
  schemaVersion: "1.0",
  documentId: `fixture-${id}`,
  template: { id: "benchmark-template", version: 1 },
  locale: "zh-CN",
  profile: {
    fullName: "林知夏",
    headline: "产品工程师 · 可验证的排版系统",
    email: "zhixia.lin@example.com",
    phone: "+86 138 0000 0000",
    location: "上海",
    website: { label: "作品集", href: "https://example.com/portfolio" }
  },
  layout: {
    kind,
    page: { size: "A4", marginMm: 16 },
    theme: { fontFamily: "Arial", accentColor: "#244b45" },
    decorations: []
  },
  sections: []
});

const item = (id: string, title: string, summary: string, extra: Partial<ResumeItem> = {}): ResumeItem => ({ id, title, summary, ...extra });
const section = (id: string, title: string, items: ResumeItem[], column: "main" | "side" = "main", type: ResumeSection["type"] = "custom"): ResumeSection => ({ id, type, title, column, visible: true, items });

const single = base("single-column");
single.sections = [
  section("experience", "工作经历", [
    item("experience-one", "星云科技 · 产品工程师", "负责简历编辑器的文档模型和渲染链路，让预览与导出使用同一份不可变快照。", { subtitle: "平台产品组", date: "2023.04—至今", bullets: ["建立可观测的分页质量门槛，覆盖中文、英文、数字与常见标点。", "将高频重排合并处理，保持输入反馈流畅。"], links: [{ label: "项目说明", href: "https://example.com/case-study" }] })
  ]),
  section("education", "教育经历", [item("education-one", "同济大学", "软件工程硕士", { date: "2020—2023" })], "main", "education")
];

const multi = base("multi-column", "two-column");
multi.sections = [
  section("experience", "核心经历", [item("multi-main-one", "排版引擎负责人", "设计受控布局树和分页适配层，支持主栏内容自动续页。", { bullets: ["定义稳定字段绑定与未知字段兜底策略。", "验证多栏阅读顺序与续页行为。"] })]),
  section("projects", "代表项目", [item("multi-main-two", "异步 PDF 验证", "使用固定快照生成 PDF，并同时检查可提取文字和页面图像。")], "main", "project"),
  section("skills", "技能", [item("skills-one", "工程能力", "TypeScript · React · Java · Spring Boot · Playwright")], "side", "skills"),
  section("education", "教育", [item("multi-education", "同济大学", "软件工程硕士")], "side", "education")
];

const portraitDecoration = base("portrait-decoration");
portraitDecoration.profile.portrait = { src: portrait, alt: "林知夏头像", slotSizeMm: 32, cropScale: 1.1 };
portraitDecoration.layout.decorations = [
  { id: "header-band", type: "header-band", anchor: "page", xMm: 0, yMm: 0, widthMm: 210, heightMm: 8, color: "#244b45" },
  { id: "accent-circle", type: "circle", anchor: "header", xMm: 170, yMm: 18, widthMm: 16, heightMm: 16, color: "#d8e5e2" }
];
portraitDecoration.sections = [section("portrait-work", "个人优势", [item("portrait-item", "兼顾内容与视觉", "头像、色块和分隔线保持在受控槽位内，不遮挡正文。")])];

const longContent = base("long-content");
longContent.sections = [section("long-experience", "长期项目经历", Array.from({ length: 12 }, (_, index) => item(
  `long-item-${index + 1}`,
  `复杂项目阶段 ${index + 1}`,
  `第 ${index + 1} 阶段围绕稳定文档模型推进。团队先冻结输入快照，再执行分页、字体和资源检查，最后生成可复核的输出结果。该过程保留中文可复制文本，并确保失败不会静默丢失整条经历。`,
  { date: `202${index % 6}.01—202${index % 6}.12`, bullets: ["记录布局问题码并阻止元素越过 A4 内容边界。", "对连续超长文本进行安全拆分，下一页保持阅读顺序。"] }
)), "main", "experience")];

const longLink = base("long-link");
longLink.sections = [section("link-section", "链接验证", [item("link-item", "公开项目", "连续链接必须换行且在 PDF 中保持可点击。", { links: [{ label: "超长链接", href: "https://example.com/projects/resumint/verification/this-is-a-deliberately-long-link-that-must-wrap-without-overflow" }] })])];

const imageOverflow = base("image-overflow");
imageOverflow.profile.portrait = { src: portrait, alt: "越界头像输入", slotSizeMm: 72, cropScale: 2 };
imageOverflow.sections = [section("image-section", "图片边界", [item("image-item", "受控头像槽位", "输入尺寸超过模板上限时钳制为 40mm，并报告问题码。")])];

const fontFailure = base("font-failure");
fontFailure.layout.theme.fontFamily = "Missing Benchmark Font";
fontFailure.sections = [section("font-section", "字体回退", [item("font-item", "中文仍可读取", "指定字体缺失时使用受控后备字体并报告问题码，不生成空白页。")])];

export const benchmarkFixtures: BenchmarkFixture[] = [
  { id: "single-column", document: single, expected: { pages: 1, text: ["林知夏", "工作经历", "同济大学"], links: ["https://example.com/portfolio", "https://example.com/case-study"], issues: [], screenshotThreshold: 0.01 } },
  { id: "multi-column", document: multi, expected: { pages: 1, text: ["核心经历", "技能", "异步 PDF 验证"], links: ["https://example.com/portfolio"], issues: [], screenshotThreshold: 0.01 } },
  { id: "portrait-decoration", document: portraitDecoration, expected: { pages: 1, text: ["头像、色块和分隔线"], links: ["https://example.com/portfolio"], issues: [], screenshotThreshold: 0.01 } },
  { id: "long-content", document: longContent, expected: { pages: 3, text: ["复杂项目阶段 1", "复杂项目阶段 12", "中文可复制文本"], links: ["https://example.com/portfolio"], issues: [], screenshotThreshold: 0.01 } },
  { id: "long-link", document: longLink, expected: { pages: 1, text: ["连续链接必须换行"], links: ["https://example.com/projects/resumint/verification/this-is-a-deliberately-long-link-that-must-wrap-without-overflow"], issues: [], screenshotThreshold: 0.01 } },
  { id: "image-overflow", document: imageOverflow, expected: { pages: 1, text: ["受控头像槽位"], links: ["https://example.com/portfolio"], issues: ["IMAGE_BOUNDS_CLAMPED"], screenshotThreshold: 0.01 } },
  { id: "font-failure", document: fontFailure, expected: { pages: 1, text: ["中文仍可读取"], links: ["https://example.com/portfolio"], issues: ["FONT_FALLBACK_USED"], screenshotThreshold: 0.01 } }
];

export const fixtureById = Object.fromEntries(benchmarkFixtures.map((fixture) => [fixture.id, fixture]));
