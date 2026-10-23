import { expect, test } from "@playwright/test";

test("guest selects a template, edits a resume, and downloads a generated PDF",async({page})=>{
  await page.goto("/templates");
  await expect(page.locator(".category-tabs")).toHaveCount(0);
  await expect(page.getByRole("button",{name:"全部",exact:true})).toHaveCount(0);
  await expect(page.getByText("白昼来信",{exact:true})).toBeVisible();
  await expect(page.getByText("排版基准模板",{exact:true})).toHaveCount(0);
  await page.goto("/editor?template=general-verdant");
  await expect(page.getByRole("button",{name:"导出 PDF"})).toBeEnabled();

  const name=page.getByRole("textbox",{name:"姓名，双击编辑"});
  await name.dblclick();
  await name.fill("端到端游客");
  await name.press("Enter");
  await expect(name).toHaveText("端到端游客");

  await page.getByRole("button",{name:"复制模块 工作经历"}).click();
  await expect(page.locator(".general-section-title")).toHaveText(["教育经历","工作经历","工作经历","个人总结"]);
  await page.getByRole("button",{name:"撤销"}).click();
  await expect(page.locator(".general-section-title")).toHaveText(["教育经历","工作经历","个人总结"]);
  await page.getByRole("button",{name:"重做"}).click();
  await expect(page.locator(".general-section-title")).toHaveText(["教育经历","工作经历","工作经历","个人总结"]);

  const upload=page.locator('input[type="file"]');
  await upload.setInputFiles({name:"portrait.png",mimeType:"image/png",buffer:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=","base64")});
  await expect(page.locator(".general-avatar img")).toBeVisible();

  const downloadPromise=page.waitForEvent("download");
  await page.getByRole("button",{name:"导出 PDF"}).click();
  const download=await downloadPromise;
  const path=await download.path();
  expect(download.suggestedFilename()).toBe("resumint-resume.pdf");
  expect(path).not.toBeNull();
  const bytes=await import("node:fs/promises").then((fs)=>fs.readFile(path!));
  expect(bytes.subarray(0,5).toString()).toBe("%PDF-");
  expect(bytes.byteLength).toBeGreaterThan(5_000);

  await page.reload();
  await expect(name).not.toHaveText("端到端游客");
});
