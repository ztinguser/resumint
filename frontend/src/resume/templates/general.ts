import type { ResumeDocument } from "../model";

export const GENERAL_TEMPLATE_ID = "general-verdant";
export const GENERAL_TEMPLATE_NAME = "设计预览（待命名）";
export const GENERAL_AVATAR_PATH = "/assets/templates/general-avatar-d57f96285da78f3a.png";

export function createGeneralDocument(): ResumeDocument {
  return {
    schemaVersion: "1.0",
    documentId: "general-working-copy",
    template: { id: GENERAL_TEMPLATE_ID, version: 1 },
    locale: "zh-CN",
    profile: {
      fullName: "林清远",
      headline: "产品设计师",
      contactTitle: "联系方式",
      email: "qingyuan.lin@example.com",
      phone: "138 0000 2026",
      location: "上海",
      website: { label: "qingyuan.design", href: "https://example.com/qingyuan" },
      portrait: { src: GENERAL_AVATAR_PATH, alt: "林清远的示例头像", slotSizeMm: 24, cropScale: 1 },
    },
    layout: {
      kind: "single",
      page: { size: "A4", marginMm: 14 },
      theme: { fontFamily: "Microsoft YaHei", accentColor: "#2f302f", fontSizePt: 9, lineHeight: 1.68 },
      decorations: [],
    },
    sections: [
      {
        id: "general-profile",
        type: "custom",
        title: "个人简介",
        column: "main",
        visible: true,
        items: [{ id: "general-profile-copy", title: "简介", summary: "拥有 8 年数字产品设计经验，擅长把复杂业务转化为清晰、易用的体验。熟悉从用户研究、交互设计到设计系统落地的完整流程，能够在多方协作中保持目标一致，并持续通过数据与反馈推动产品迭代。" }],
      },
      {
        id: "general-education",
        type: "education",
        title: "教育经历",
        column: "main",
        visible: true,
        items: [
          { id: "general-education-1", title: "江南大学", subtitle: "视觉传达设计 · 学士", date: "2012—2016", summary: "主修品牌设计、信息设计与交互设计，GPA 3.7/4.0" },
          { id: "general-education-2", title: "同济大学", subtitle: "设计创意学院 · 进修", date: "2018—2019", summary: "服务设计与用户体验研究" },
        ],
      },
      {
        id: "general-experience",
        type: "experience",
        title: "工作经历",
        column: "main",
        visible: true,
        items: [
          { id: "general-work-1", title: "星海科技", subtitle: "高级产品设计师", date: "2022—至今", summary: "负责核心产品从需求洞察、方案定义到设计交付的完整流程，与产品、研发和运营团队共同梳理复杂业务场景。建立跨团队设计评审机制，并推动设计系统覆盖多个产品线；通过定期复盘和指标跟踪，持续提升体验一致性、方案质量与交付效率。" },
          { id: "general-work-2", title: "有光互动", subtitle: "产品设计师", date: "2018—2022", summary: "参与企业服务产品与移动端体验设计，独立完成用户访谈、信息架构和关键流程原型。通过可用性测试与数据分析持续优化方案，降低新用户上手成本；同时整理组件使用原则和交付清单，帮助团队沉淀可复用的设计规范。" },
        ],
      },
      {
        id: "general-summary",
        type: "custom",
        title: "个人总结",
        column: "main",
        visible: true,
        items: [{ id: "general-summary-copy", title: "总结", summary: "相信好的设计应当克制、清晰并解决真实问题。重视协作，也乐于把经验沉淀为团队可复用的方法与工具；面对新领域时保持好奇，通过观察、验证和复盘做出可靠判断。希望与尊重用户、重视长期价值的团队一起工作，让每一次迭代都更接近问题本质。" }],
      },
    ],
  };
}
