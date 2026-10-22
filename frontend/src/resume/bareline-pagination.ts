import type {ResumeDocument} from "./model";
import {paginateResume} from "./pagination";

export function paginateBarelineResume(document:ResumeDocument){
  return paginateResume(document,{firstMain:56,continuationMain:60,firstSide:72,continuationSide:74,keepItemsTogether:true});
}
