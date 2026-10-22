import{expect,test}from"@playwright/test";

test("template library shows the second candidate as a structure-only skeleton",async({page})=>{
  await page.route("**/api/templates",route=>route.fulfill({status:200,contentType:"application/json",body:"[]"}));
  await page.goto("/templates");
  const card=page.locator(".template-card").filter({hasText:"灰度之间"});
  await expect(card.locator(".centered-skeleton")).toBeVisible();
  await expect(card.locator(".centered-skeleton")).not.toContainText("林清远");
  await expect(card.getByText("待确认",{exact:true})).toBeVisible();
});

test("second candidate opens with editable Chinese content and working element styles",async({page})=>{
  await page.goto("/editor?template=business-ledger&qa=centered-candidate");
  const resume=page.locator(".centered-template-page");
  await expect(resume).toBeVisible();
  await expect(resume.getByText("林清远",{exact:true})).toBeVisible();
  await expect(resume.getByText("联系方式",{exact:true})).toBeVisible();
  await expect(resume.getByText("主要技能",{exact:true})).toBeVisible();
  await expect(resume.getByText("教育经历",{exact:true})).toBeVisible();
  await expect(resume.getByText("工作经历",{exact:true})).toBeVisible();
  await resume.screenshot({path:"test-results/candidates/centered-editor.png"});

  const location=resume.locator('[data-element-id="contact.centered-location.value"]');
  await location.click();
  await expect(page.locator(".element-style-panel").getByLabel("元素文字颜色")).toHaveValue("#858585");
  await expect(page.getByRole("button",{name:"元素字体常规"})).toHaveAttribute("aria-pressed","true");
  await page.getByRole("button",{name:"元素字体粗体"}).click();
  await expect(location).toHaveCSS("font-weight","700");
  await page.getByRole("button",{name:"恢复默认"}).click();
  await expect(location).toHaveCSS("font-weight","400");
  await expect(page.getByRole("button",{name:"元素字体常规"})).toHaveAttribute("aria-pressed","true");

  const contactCount=await resume.locator(".centered-contact").count();
  await resume.locator(".centered-contacts").hover();
  await expect(page.getByRole("button",{name:"新增联系方式"})).toBeVisible();
  await page.getByRole("button",{name:"新增联系方式"}).click();
  await expect(resume.locator(".centered-contact")).toHaveCount(contactCount+1);
  await location.dblclick();
  await location.fill("杭州市西湖区");
  await location.press("Enter");
  await expect(location).toHaveText("杭州市西湖区");

  const downloadPromise=page.waitForEvent("download");
  await page.getByRole("button",{name:"导出 PDF"}).click();
  const download=await downloadPromise;
  const path=await import("node:path");
  const fs=await import("node:fs/promises");
  const exportPath=path.resolve(process.cwd(),"../tmp/pdfs/centered-template-export.pdf");
  await fs.mkdir(path.dirname(exportPath),{recursive:true});
  await download.saveAs(exportPath);
  const bytes=await fs.readFile(exportPath);
  expect(bytes.subarray(0,5).toString()).toBe("%PDF-");
  expect(bytes.byteLength).toBeGreaterThan(5_000);
  expect(bytes.toString("latin1").match(/\/Type\s*\/Page\b/g)).toHaveLength(1);
});
