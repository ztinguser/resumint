import type{ResumeDocument}from"./model";
import{paginateResume}from"./pagination";

export function paginateCenteredResume(document:ResumeDocument){
  return paginateResume(document,{keepItemsTogether:true});
}
