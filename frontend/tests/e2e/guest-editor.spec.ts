import { expect, test } from "@playwright/test";

test("guest selects a template, edits a resume, and downloads a generated PDF",async({page})=>{
  await page.goto("/templates");
  await expect(page.locator(".category-tabs")).toHaveCount(0);
  await expect(page.getByRole("button",{name:"全部",exact:true})).toHaveCount(0);
  await expect(page.getByText("设计预览（待命名）",{exact:true})).toBeVisible();
  await expect(page.getByText("排版基准模板",{exact:true})).toBeVisible();
  await page.getByRole("article").filter({hasText:"排版基准模板"}).getByRole("link",{name:"使用此模板"}).click();
  await expect(page.getByRole("button",{name:"导出 PDF"})).toBeEnabled();

  const name=page.getByLabel("姓名");
  await name.fill("端到端游客");
  await expect(page.locator(".resume-page h1").first()).toHaveText("端到端游客");
  await page.getByLabel("个人链接").fill("invalid-link");
  await expect(page.getByText("链接需以 http:// 或 https:// 开头")).toBeVisible();
  await page.getByLabel("个人链接").fill("https://example.com/e2e");

  await page.getByRole("button",{name:"＋ 模块"}).click();
  await page.getByLabel("模块名称").fill("端到端模块");
  await page.getByRole("button",{name:"＋ 添加条目"}).click();
  await page.getByLabel("名称").last().fill("游客条目");
  await page.getByRole("button",{name:"＋ 字段"}).click();
  await page.getByLabel("字段值").last().fill("自定义值");
  await page.getByRole("button",{name:"撤销"}).click();
  await expect(page.getByLabel("字段值").last()).toHaveValue("字段内容");
  await page.getByRole("button",{name:"重做"}).click();
  await expect(page.getByLabel("字段值").last()).toHaveValue("自定义值");

  const upload=page.locator('input[type="file"]');
  await upload.setInputFiles({name:"portrait.png",mimeType:"image/png",buffer:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=","base64")});
  await expect(page.locator(".portrait")).toBeVisible();
  await page.getByRole("button",{name:"装饰",exact:true}).click();
  await page.getByRole("button",{name:"＋ 添加",exact:true}).click();
  await expect(page.locator(".decoration-list button")).toHaveCount(2);

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
  await expect(page.getByLabel("姓名")).not.toHaveValue("端到端游客");
});
