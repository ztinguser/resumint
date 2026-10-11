import { describe, expect, test } from "vitest";
import { benchmarkFixtures } from "../fixtures/benchmark-fixtures";
import { detectLayoutIssues, paginateResume } from "../../src/resume/pagination";
import { validateResumeDocument } from "../../src/resume/validation";

describe("shared resume document contract", () => {
  test.each(benchmarkFixtures)("$id conforms to schema and fixed expectations", (fixture) => {
    const validation = validateResumeDocument(fixture.document);
    expect(validation.errors).toEqual([]);
    expect(validation.valid).toBe(true);
    expect(paginateResume(fixture.document)).toHaveLength(fixture.expected.pages);
    expect(detectLayoutIssues(fixture.document)).toEqual(fixture.expected.issues);
  });

  test("rejects unstable ids and arbitrary image URLs", () => {
    const invalid = structuredClone(benchmarkFixtures[0].document);
    invalid.documentId = "1";
    invalid.profile.portrait = { src: "https://tracker.example/avatar.png", alt: "x", slotSizeMm: 24 };
    const result = validateResumeDocument(invalid);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThanOrEqual(2);
  });

  test("accepts persisted element visibility overrides",()=>{
    const document=structuredClone(benchmarkFixtures[0].document);
    document.hiddenElementIds=["item.work-experience.title","profile.email"];
    expect(validateResumeDocument(document)).toMatchObject({valid:true,errors:[]});
  });
});
