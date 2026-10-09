import type { ResumeDocument } from "@/resume/model";

type ExportStatus = { id: string; status: "queued" | "running" | "succeeded" | "failed" | "cancelled" | "expired"; credential?: string; errorCode?: string };
const wait = (milliseconds: number, signal?: AbortSignal) => new Promise<void>((resolve, reject) => { const timeout=setTimeout(resolve,milliseconds); signal?.addEventListener("abort",()=>{clearTimeout(timeout);reject(new DOMException("导出已取消","AbortError"));},{once:true}); });
export async function exportResume(document: ResumeDocument, onStatus: (status: ExportStatus) => void, signal?: AbortSignal): Promise<void> {
  const key = crypto.randomUUID();
  const created = await fetch("/api/exports", { method: "POST", headers: { "Content-Type": "application/json", "Idempotency-Key": key }, body: JSON.stringify({ document }) });
  if (!created.ok) throw await apiError(created);
  const first = await created.json() as ExportStatus;
  const credential = first.credential!; onStatus(first);
  const cancel = () => fetch(`/api/exports/${first.id}`, { method: "DELETE", headers: { "X-Export-Credential": credential }, keepalive: true }).catch(() => undefined);
  signal?.addEventListener("abort",cancel,{once:true});
  for (let attempt = 0; attempt < 90; attempt += 1) {
    await wait(500,signal);
    const response = await fetch(`/api/exports/${first.id}`, { headers: { "X-Export-Credential": credential }, cache: "no-store" });
    if (!response.ok) throw await apiError(response);
    const status = await response.json() as ExportStatus; onStatus(status);
    if (status.status === "succeeded") {
      const file = await fetch(`/api/exports/${first.id}/file`, { headers: { "X-Export-Credential": credential }, cache: "no-store" });
      if (!file.ok) throw await apiError(file);
      const url = URL.createObjectURL(await file.blob()); const anchor = window.document.createElement("a"); anchor.href = url; anchor.download = "resumint-resume.pdf"; anchor.click(); URL.revokeObjectURL(url); signal?.removeEventListener("abort",cancel); return;
    }
    if (["failed", "cancelled", "expired"].includes(status.status)) throw new Error(status.errorCode ?? `导出${status.status}`);
  }
  signal?.removeEventListener("abort",cancel); throw new Error("导出等待超时，请重试");
}
async function apiError(response: Response) { const body = await response.json().catch(() => null) as { message?: string } | null; return new Error(body?.message ?? `请求失败（${response.status}）`); }
