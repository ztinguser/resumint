import{expect,test}from"@playwright/test";

test.beforeEach(async({page})=>{
  await page.route("**/api/me",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({userId:"admin-review",maskedPhone:"+86 135****5102",avatar:"DEFAULT",passwordSet:true,role:"ADMIN"})}));
});

test("administrator candidate workbench keeps release gated and renders all three visual directions",async({page})=>{
  await page.goto("/admin/templates");
  await expect(page.getByRole("heading",{name:"远山青"})).toBeVisible();
  await expect(page.getByRole("button",{name:"发布不可变版本"})).toBeDisabled();
  await expect(page.getByText("待确认 · 未发布 · 未上架")).toBeVisible();
  await page.screenshot({path:"test-results/candidates/general-verdant.png"});

  await page.getByRole("button",{name:/商务.*蓝图纪事/}).click();
  await expect(page.getByRole("heading",{name:"蓝图纪事"})).toBeVisible();
  await expect(page.locator(".template-business-ledger")).toBeVisible();
  await page.screenshot({path:"test-results/candidates/business-ledger.png"});

  await page.getByRole("button",{name:/简约.*留白信笺/}).click();
  await expect(page.getByRole("heading",{name:"留白信笺"})).toBeVisible();
  await expect(page.locator(".template-minimal-bareline")).toBeVisible();
  await page.getByRole("button",{name:"自定义字段兜底"}).click();
  await expect(page.getByText("研究方法：访谈 · 可用性测试")).toBeVisible();
  await page.screenshot({path:"test-results/candidates/minimal-bareline.png"});
});
