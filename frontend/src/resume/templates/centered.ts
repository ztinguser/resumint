import type {ResumeDocument} from "../model";
import {GENERAL_AVATAR_PATH} from "./general";

export const CENTERED_TEMPLATE_ID="business-ledger";
export const CENTERED_TEMPLATE_NAME="灰度之间";

export function createCenteredDocument():ResumeDocument{
  return {
    schemaVersion:"1.0",
    documentId:"centered-working-copy",
    template:{id:CENTERED_TEMPLATE_ID,version:1},
    locale:"zh-CN",
    profile:{
      fullName:"林清远",
      headline:"文员",
      contactTitle:"联系方式",
      email:"qingyuan.lin@example.com",
      phone:"138 0000 2026",
      location:"上海市静安区",
      website:{label:"qingyuan.design",href:"https://example.com/qingyuan"},
      contacts:[
        {id:"centered-phone",label:"电话",value:"138 0000 2026"},
        {id:"centered-email",label:"邮箱",value:"qingyuan.lin@example.com"},
        {id:"centered-location",label:"所在地",value:"上海市静安区"},
        {id:"centered-website",label:"个人主页",value:"qingyuan.design",href:"https://example.com/qingyuan"},
      ],
      portrait:{src:GENERAL_AVATAR_PATH,alt:"林清远的示例头像",slotSizeMm:30,cropScale:1},
    },
    layout:{
      kind:"two-column",
      page:{size:"A4",marginMm:14},
      theme:{fontFamily:"Microsoft YaHei",accentColor:"#242424",fontSizePt:9,lineHeight:1.72},
      decorations:[],
    },
    hiddenElementIds:["item.centered-profile-copy.title"],
    sections:[
      {id:"centered-profile",type:"custom",title:"个人简介",column:"side",visible:true,items:[{id:"centered-profile-copy",title:"简介",summary:"拥有 8 年品牌与内容设计经验，擅长把复杂信息整理成清晰、准确、易于理解的表达。熟悉资料归档、内容编排与跨团队协作。"}]},
      {id:"centered-skills",type:"skills",title:"主要技能",column:"side",visible:true,items:[
        {id:"centered-skill-1",title:"文档排版"},{id:"centered-skill-2",title:"资料归档"},{id:"centered-skill-3",title:"数据整理"},{id:"centered-skill-4",title:"沟通协调"},{id:"centered-skill-5",title:"办公软件"},
      ]},
      {id:"centered-education",type:"education",title:"教育经历",column:"main",visible:true,items:[
        {id:"centered-education-1",title:"江南大学",subtitle:"视觉传达设计 · 学士",date:"2008.09—2012.06",summary:"主修品牌设计、信息设计与交互设计，GPA 3.7/4.0"},
        {id:"centered-education-2",title:"同济大学",subtitle:"设计创意学院 · 进修",date:"2012.09—2014.06",summary:"服务设计与用户体验研究"},
      ]},
      {id:"centered-experience",type:"experience",title:"工作经历",column:"main",visible:true,items:[
        {id:"centered-work-1",title:"星海科技",subtitle:"高级行政文员",date:"2018.03—至今",summary:"负责日常文件整理、会议支持与跨部门事项跟进，建立统一的资料归档规范，提升团队检索与协作效率。"},
        {id:"centered-work-2",title:"有光互动",subtitle:"行政文员",date:"2014.07—2018.02",summary:"完成合同、报表与项目资料的分类归档，协助安排会议和访客接待，并持续优化常用文档模板。"},
      ]},
    ],
  };
}
