import Ajv2020, { type ErrorObject } from "ajv/dist/2020";
import addFormats from "ajv-formats";
import schema from "./schema/resume-document.schema.json";
import type { ResumeDocument } from "./model";

const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);
const validateDocument = ajv.compile<ResumeDocument>(schema);

export function validateResumeDocument(value: unknown): { valid: boolean; errors: ErrorObject[] } {
  const valid = validateDocument(value);
  return { valid, errors: validateDocument.errors ?? [] };
}

export function assertResumeDocument(value: unknown): asserts value is ResumeDocument {
  const result = validateResumeDocument(value);
  if (!result.valid) {
    throw new Error(result.errors.map((error) => `${error.instancePath || "/"} ${error.message}`).join("; "));
  }
}
