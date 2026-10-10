import type { ResumeDocument } from "@/resume/model";

export type EditorHistory = { past: ResumeDocument[]; present: ResumeDocument; future: ResumeDocument[] };
export type HistoryAction = { type: "commit"; document: ResumeDocument } | { type: "replace"; document: ResumeDocument } | { type: "undo" } | { type: "redo" };
export function historyReducer(state: EditorHistory, action: HistoryAction): EditorHistory {
  if (action.type === "undo" && state.past.length) return { past: state.past.slice(0, -1), present: state.past.at(-1)!, future: [state.present, ...state.future] };
  if (action.type === "redo" && state.future.length) return { past: [...state.past, state.present], present: state.future[0], future: state.future.slice(1) };
  if (action.type === "commit") return { past: [...state.past.slice(-49), state.present], present: action.document, future: [] };
  if (action.type === "replace") return { past: [], present: action.document, future: [] };
  return state;
}

export const cloneDocument = (document: ResumeDocument) => structuredClone(document);
export const move = <T,>(items: T[], from: number, to: number): T[] => { const next = [...items]; const [item] = next.splice(from, 1); next.splice(to, 0, item); return next; };
