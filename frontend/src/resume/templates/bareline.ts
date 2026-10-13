import type {ResumeDocument} from "../model";

export const BARELINE_TEMPLATE_ID="minimal-bareline";
export const BARELINE_TEMPLATE_NAME="设计预览 03（待命名）";

export function createBarelineDocument():ResumeDocument{
  return {
    schemaVersion:"1.0",
    documentId:"bareline-working-copy",
    template:{id:BARELINE_TEMPLATE_ID,version:1},
    locale:"zh-CN",
    profile:{
      fullName:"林清远，",
      headline:"体验设计师",
      email:"qingyuan.lin@example.com",
      contactTitle:"联系方式",
      contacts:[
        {id:"bareline-website",label:"个人主页",value:"qingyuan.design",href:"https://example.com/qingyuan"},
        {id:"bareline-email",label:"邮箱",value:"qingyuan.lin@example.com"},
        {id:"bareline-phone",label:"电话",value:"138 0000 2026"},
      ],
    },
    layout:{kind:"two-column",page:{size:"A4",marginMm:14},theme:{fontFamily:"Arial",accentColor:"#202020",fontSizePt:8.5,lineHeight:1.48},decorations:[]},
    sections:[
      {id:"bareline-skills",type:"skills",title:"专业技能",column:"side",visible:true,items:[
        {id:"bareline-skill-1",title:"用户研究"},{id:"bareline-skill-2",title:"交互设计"},{id:"bareline-skill-3",title:"视觉设计"},{id:"bareline-skill-4",title:"沟通与协作"},{id:"bareline-skill-5",title:"用户测试"},{id:"bareline-skill-6",title:"适应力与持续学习"},
      ]},
      {id:"bareline-tools",type:"skills",title:"常用工具",column:"side",visible:true,items:[
        {id:"bareline-tool-1",title:"Figma"},{id:"bareline-tool-2",title:"Sketch"},{id:"bareline-tool-3",title:"Adobe Creative Suite"},{id:"bareline-tool-4",title:"Photoshop"},{id:"bareline-tool-5",title:"Illustrator"},{id:"bareline-tool-6",title:"Axure"},{id:"bareline-tool-7",title:"Principle"},{id:"bareline-tool-8",title:"Notion"},{id:"bareline-tool-9",title:"Webflow"},
      ]},
      {id:"bareline-experience",type:"experience",title:"工作经历",column:"main",visible:true,items:[
        {id:"bareline-work-1",title:"星海科技",subtitle:"体验设计师",date:"2023.01—至今",summary:"• 负责核心产品的用户研究与体验分析，从访谈和行为数据中识别关键问题并形成清晰的设计结论。\n• 与产品、研发团队协作完成线框、交互原型和高保真方案，并维护跨端组件与交互规范。\n• 主持设计评审与可用性测试，收集真实用户反馈，持续优化复杂业务流程与信息呈现。"},
        {id:"bareline-work-2",title:"有光互动",subtitle:"自由设计师",date:"2022.06—2022.12",summary:"• 为移动端与网页产品设计信息架构、交互原型和视觉界面，覆盖从概念验证到正式交付的完整过程。\n• 基于用户反馈和可用性测试迭代方案，整理关键流程说明、交互规范与可复用组件。\n• 与开发人员紧密协作，确保设计准确落地，并在不同设备上满足一致性与无障碍标准。"},
      ]},
      {id:"bareline-education",type:"education",title:"教育经历",column:"main",visible:true,items:[
        {id:"bareline-education-1",title:"江南大学",subtitle:"工业设计 · 学士",date:"2018.06—2022.06",summary:"• 主修交互设计、用户研究、设计心理学与信息架构，并参与多项跨学科产品设计课题。\n• 完成面向社区公共服务的移动产品毕业设计，从调研、原型到测试形成完整方案。"},
        {id:"bareline-education-2",title:"同济大学",subtitle:"体验设计进修",date:"2023.01—2023.02",summary:"• 参与以用户为中心的密集型设计、原型与视觉课程，完成多轮设计评审和用户验证。\n• 与团队协作完成公益网站与健康应用的多端设计项目，并交付可执行的设计规范。"},
      ]},
    ],
  };
}
