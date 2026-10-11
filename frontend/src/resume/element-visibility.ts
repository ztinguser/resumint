import type {ResumeDocument} from "./model";

export function isElementVisible(document:ResumeDocument,id:string):boolean{return !document.hiddenElementIds?.includes(id);}

export function hideElement(document:ResumeDocument,id:string):void{
  const hidden=document.hiddenElementIds??=[];
  if(!hidden.includes(id))hidden.push(id);
}
