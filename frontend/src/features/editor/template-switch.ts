import type {ResumeDocument} from "@/resume/model";
import {cloneDocument} from "./history";

export type SwitchTemplate={id:string;name:string;tags:string[];version:number;config?:{visualKey?:string}};

export function applyTemplateToDocument(source:ResumeDocument,target:SwitchTemplate):ResumeDocument{
  const next=cloneDocument(source);next.template={id:target.id,version:target.version};
  const visual=target.config?.visualKey;
  if(visual==="ledger"){next.layout.kind="two-column";next.layout.page.marginMm=16;next.layout.theme.accentColor="#17324d";}
  else if(visual==="bareline"){next.layout.kind="single";next.layout.page.marginMm=20;next.layout.theme.accentColor="#202020";}
  else if(visual==="verdant"){next.layout.kind="single";next.layout.page.marginMm=16;next.layout.theme.accentColor="#315b52";}
  next.layout.decorations=[];return next;
}
