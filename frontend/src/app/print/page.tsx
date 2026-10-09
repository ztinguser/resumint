"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { ResumePrintDocument } from "@/resume/ResumePrintDocument";
import { fixtureById } from "../../../tests/fixtures/benchmark-fixtures";
import "./print.css";

function PrintFixture() {
  const fixtureId = useSearchParams().get("fixture") ?? "single-column";
  const fixture = fixtureById[fixtureId] ?? fixtureById["single-column"];
  return <ResumePrintDocument initialDocument={fixture.document} />;
}

export default function PrintPage() {
  return <Suspense fallback={null}><PrintFixture /></Suspense>;
}
