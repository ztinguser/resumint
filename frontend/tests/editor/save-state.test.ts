import{describe,expect,it}from"vitest";import{edited,initialSaveState,isSaveInFlight,loadedSaved,saveFailed,saveStarted,saveSucceeded}from"../../src/features/editor/save-state";
describe("manual save state",()=>{
  it("keeps edits made during a save dirty",()=>{let state=edited(loadedSaved());state=saveStarted(state);state=edited(state);state=saveSucceeded(state);expect(state.status).toBe("dirty");expect(state.savedRevision).toBe(1);expect(state.localRevision).toBe(2);});
  it("keeps the save action blocked while an edited save is still in flight",()=>{let state=saveStarted(edited(loadedSaved()));state=edited(state);expect(state.status).toBe("dirty");expect(isSaveInFlight(state)).toBe(true);state=saveSucceeded(state);expect(isSaveInFlight(state)).toBe(false);});
  it("never loses a failed working copy",()=>{let state=saveStarted(edited(initialSaveState));state=saveFailed(state);expect(state.status).toBe("failed");expect(state.localRevision).toBe(1);});
});
