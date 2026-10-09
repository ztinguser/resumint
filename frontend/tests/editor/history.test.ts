import { describe, expect, test } from "vitest";
import { createBenchmarkDocument } from "../../src/resume/templates/benchmark";
import { historyReducer, move } from "../../src/features/editor/history";

describe("guest editor history", () => {
  test("undo and redo restore one logical edit", () => {
    const original=createBenchmarkDocument();
    const edited=structuredClone(original);edited.profile.fullName="新的姓名";
    const committed=historyReducer({past:[],present:original,future:[]},{type:"commit",document:edited});
    const undone=historyReducer(committed,{type:"undo"});
    expect(undone.present.profile.fullName).toBe(original.profile.fullName);
    expect(historyReducer(undone,{type:"redo"}).present.profile.fullName).toBe("新的姓名");
  });

  test("reordering does not mutate the source array", () => {
    const source=["a","b","c"];
    expect(move(source,2,0)).toEqual(["c","a","b"]);
    expect(source).toEqual(["a","b","c"]);
  });
});
