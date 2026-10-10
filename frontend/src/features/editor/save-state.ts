export type SaveStatus="unsaved"|"saving"|"saved"|"dirty"|"failed";
export type SaveState={status:SaveStatus;localRevision:number;savingRevision:number|null;savedRevision:number|null};
export const initialSaveState:SaveState={status:"unsaved",localRevision:0,savingRevision:null,savedRevision:null};
export function edited(state:SaveState):SaveState{return{...state,localRevision:state.localRevision+1,status:state.status==="unsaved"?"unsaved":"dirty"};}
export function saveStarted(state:SaveState):SaveState{return{...state,status:"saving",savingRevision:state.localRevision};}
export function saveSucceeded(state:SaveState):SaveState{const submitted=state.savingRevision??state.localRevision;return{...state,savingRevision:null,savedRevision:submitted,status:state.localRevision===submitted?"saved":"dirty"};}
export function saveFailed(state:SaveState):SaveState{return{...state,savingRevision:null,status:"failed"};}
export function loadedSaved(localRevision=0):SaveState{return{status:"saved",localRevision,savingRevision:null,savedRevision:localRevision};}
