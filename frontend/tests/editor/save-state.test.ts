import{describe,expect,it}from"vitest";import{edited,initialSaveState,loadedSaved,saveFailed,saveStarted,saveSucceeded}from"../../src/features/editor/save-state";
describe("manual save state",()=>{
  it("keeps edits made during a save dirty",()=>{let state=edited(loadedSaved());state=saveStarted(state);state=edited(state);state=saveSucceeded(state);expect(state.status).toBe("dirty");expect(state.savedRevision).toBe(1);expect(state.localRevision).toBe(2);});
  it("never loses a failed working copy",()=>{let state=saveStarted(edited(initialSaveState));state=saveFailed(state);expect(state.status).toBe("failed");expect(state.localRevision).toBe(1);});
});
