import{expect,test}from"@playwright/test";

test.beforeEach(async({page})=>{
  await page.route("**/api/me",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({userId:"admin-review",maskedPhone:"+86 135****5102",avatar:"DEFAULT",passwordSet:true,role:"ADMIN"})}));
});

test("administrator candidate workbench gates release until a saved draft exists and renders all three candidates",async({page})=>{
  await page.goto("/admin/templates");
  await expect(page.getByRole("heading",{name:"白昼来信"})).toBeVisible();
  await expect(page.getByRole("button",{name:"发布不可变版本"})).toBeDisabled();
  await expect(page.getByText("尚未创建",{exact:true})).toBeVisible();
  await page.screenshot({path:"test-results/candidates/general-verdant.png"});

  await page.getByRole("button",{name:/候选 2.*灰度之间/}).click();
  await expect(page.getByRole("heading",{name:"灰度之间"})).toBeVisible();
  await expect(page.locator(".template-business-ledger")).toBeVisible();
  await page.screenshot({path:"test-results/candidates/business-ledger.png"});

  await page.getByRole("button",{name:/候选 3.*负空间/}).click();
  await expect(page.getByRole("heading",{name:"负空间"})).toBeVisible();
  await expect(page.locator(".template-minimal-bareline")).toBeVisible();
  await page.getByRole("button",{name:"自定义字段兜底"}).click();
  await expect(page.getByText("研究方法：访谈 · 可用性测试")).toBeVisible();
  await page.screenshot({path:"test-results/candidates/minimal-bareline.png"});
});
