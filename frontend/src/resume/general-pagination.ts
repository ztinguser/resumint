import type {ResumeDocument} from "./model";
import {paginateResume,type RenderPage} from "./pagination";

export function paginateGeneralResume(document:ResumeDocument):RenderPage[]{
  const body=structuredClone(document);
  body.sections=body.sections.filter(section=>section.id!=="general-profile");
  return paginateResume(body,{firstMain:58,continuationMain:64});
}
