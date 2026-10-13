import type {ResumeDocument} from "./model";
import {paginateResume} from "./pagination";

export function paginateBarelineResume(document:ResumeDocument){
  return paginateResume(document,{firstMain:72,continuationMain:74,firstSide:72,continuationSide:74});
}
