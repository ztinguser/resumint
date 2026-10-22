import{expect,test}from"@playwright/test";

test("template library shows the third candidate as a structure-only skeleton",async({page})=>{
  await page.route("**/api/templates",route=>route.fulfill({status:200,contentType:"application/json",body:"[]"}));
  await page.goto("/templates");
  const card=page.locator(".template-card").filter({hasText:"负空间"});
  await expect(card.locator(".bareline-skeleton")).toBeVisible();
  await expect(card.locator(".bareline-skeleton")).not.toContainText("林清远");
  await expect(card.getByText("待确认",{exact:true})).toBeVisible();
});

test("third candidate supports direct editing, additions, styling and a one-page PDF",async({page})=>{
  await page.goto("/editor?template=minimal-bareline&qa=bareline-candidate");
  const resume=page.locator(".bareline-template-page");
  await expect(resume).toBeVisible();
  const canvasControls=page.locator(".workbench-topbar .topbar-preview-controls");
  await expect(canvasControls).toBeVisible();
  await expect(canvasControls).toContainText("导出约 1 页");
  await expect(page.locator(".preview-panel>.preview-toolbar")).toHaveCount(0);
  expect(await page.locator(".preview-scroll").evaluate(element=>element.getBoundingClientRect().top-element.parentElement!.getBoundingClientRect().top)).toBeLessThan(2);
  await page.getByRole("button",{name:"放大画布"}).click();
  await expect(page.getByRole("button",{name:"恢复 100%"})).toHaveText("110%");
  await page.getByRole("button",{name:"恢复 100%"}).click();
  await expect(page.getByRole("button",{name:"恢复 100%"})).toHaveText("100%");
  await expect(resume.getByText("林清远，",{exact:true})).toBeVisible();
  await expect(resume.getByText("专业技能",{exact:true})).toBeVisible();
  await expect(resume.getByText("常用工具",{exact:true})).toBeVisible();
  await expect(resume.getByText("工作经历",{exact:true})).toBeVisible();
  await expect(resume.getByText("教育经历",{exact:true})).toBeVisible();

  const website=resume.locator('[data-element-id="contact.bareline-website.value"]');
  await website.click();
  await expect(page.getByRole("button",{name:"元素字体常规"})).toHaveAttribute("aria-pressed","true");
  await page.getByRole("button",{name:"元素字体粗体"}).click();
  await expect(website).toHaveCSS("font-weight","700");
  await page.getByRole("button",{name:"恢复默认"}).click();

  const contactCount=await resume.locator(".bareline-contact").count();
  await expect(page.getByRole("button",{name:"新增联系方式"})).toBeVisible();
  await page.getByRole("button",{name:"新增联系方式"}).click();
  await expect(resume.locator(".bareline-contact")).toHaveCount(contactCount+1);

  const skillCount=await resume.locator('[data-section-id="bareline-skills"] .general-entry').count();
  await resume.locator('[data-section-id="bareline-skills"] .general-entry').last().hover();
  await page.getByRole("button",{name:/在用户测试后新增一项/}).click();
  await expect(resume.locator('[data-section-id="bareline-skills"] .general-entry')).toHaveCount(skillCount+1);

  await resume.screenshot({path:"test-results/candidates/bareline-editor.png"});
  const downloadPromise=page.waitForEvent("download");
  await page.getByRole("button",{name:"导出 PDF"}).click();
  const download=await downloadPromise;
  const path=await import("node:path");
  const fs=await import("node:fs/promises");
  const exportPath=path.resolve(process.cwd(),"../tmp/pdfs/bareline-template-export.pdf");
  await fs.mkdir(path.dirname(exportPath),{recursive:true});
  await download.saveAs(exportPath);
  const bytes=await fs.readFile(exportPath);
  expect(bytes.subarray(0,5).toString()).toBe("%PDF-");
  expect(bytes.byteLength).toBeGreaterThan(5_000);
  expect(bytes.toString("latin1").match(/\/Type\s*\/Page\b/g)).toHaveLength(1);
});
