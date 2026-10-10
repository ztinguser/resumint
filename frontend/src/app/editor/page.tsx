import { GuestWorkbench } from "@/features/editor/GuestWorkbench";
export default async function EditorPage({searchParams}:{searchParams:Promise<{template?:string;draft?:string}>}){const query=await searchParams;return <GuestWorkbench templateId={query.template??"benchmark-template"} initialDraftId={query.draft}/>;}
