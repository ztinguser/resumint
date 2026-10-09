import type { ResumeDocument } from "../model";

export function createBenchmarkDocument(): ResumeDocument {
  return {
    schemaVersion: "1.0", documentId: "guest-working-copy", template: { id: "benchmark-template", version: 1 }, locale: "zh-CN",
    profile: { fullName: "张伟", headline: "产品经理", email: "zhangwei@example.com", phone: "138 0000 8888", location: "北京", website: { label: "作品集", href: "https://example.com/portfolio" } },
    layout: { kind: "single", page: { size: "A4", marginMm: 16 }, theme: { fontFamily: "Arial", accentColor: "#202221" }, decorations: [{ id: "header-divider", type: "divider", anchor: "header", xMm: 16, yMm: 55, widthMm: 178, heightMm: 0.4, color: "#202221", zIndex: 1, locked: false }] },
    sections: [
      { id: "profile-summary", type: "custom", title: "个人简介", column: "main", visible: true, items: [{ id: "profile-summary-item", title: "职业概况", summary: "5 年互联网产品经验，擅长从 0 到 1 打造产品，关注用户体验与数据驱动。" }] },
      { id: "work-experience", type: "experience", title: "工作经历", column: "main", visible: true, items: [
        { id: "work-byte", title: "字节跳动", subtitle: "产品经理", date: "2022.07—至今", bullets: ["负责核心产品的需求调研、方案设计与落地。", "推动数据驱动迭代，提升整体转化效率。"] },
        { id: "work-ant", title: "蚂蚁集团", subtitle: "高级产品经理", date: "2020.03—2022.06", bullets: ["参与支付核心业务规划与迭代。"] }
      ] },
      { id: "education", type: "education", title: "教育经历", column: "main", visible: true, items: [{ id: "education-bupt", title: "北京大学", subtitle: "计算机科学与技术 · 本科", date: "2014.09—2018.06", bullets: ["GPA 3.7/4.0"] }] },
      { id: "projects", type: "project", title: "项目经历", column: "main", visible: true, items: [{ id: "project-resume", title: "结构化简历编辑器", summary: "以统一文档模型驱动编辑、预览和导出。", links: [{ label: "项目链接", href: "https://example.com/resume" }] }] },
      { id: "skills", type: "skills", title: "技能", column: "side", visible: true, items: [{ id: "skills-product", title: "产品与工程", summary: "产品规划 · 数据分析 · TypeScript · Java" }] }
    ]
  };
}
