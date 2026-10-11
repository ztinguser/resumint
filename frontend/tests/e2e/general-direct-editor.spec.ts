import { expect, test } from "@playwright/test";

test("general template supports module insertion, consistent contacts, and element styling", async ({ page }) => {
  await page.goto("/editor?template=general-verdant&qa=direct-editor");

  await page.getByRole("button", { name: "在工作经历后新增模块" }).click();
  await page.getByRole("menuitem", { name: "项目经历" }).click();
  await expect(page.locator(".general-section-title")).toHaveText([
    "教育经历",
    "工作经历",
    "项目经历",
    "个人总结",
  ]);

  const contacts = page.locator(".contact-list-wrap li");
  await expect(contacts).toHaveCount(4);
  await page.getByRole("button", { name: "新增联系方式" }).click();
  await expect(contacts).toHaveCount(5);
  await expect(contacts.last()).toHaveText("双击填写");
  await expect(contacts.last()).not.toContainText("其他");

  const schoolName = page.locator('[data-element-id="item.general-education-1.title"]');
  await schoolName.click();
  await expect(page.getByRole("heading", { name: "条目名称" })).toBeVisible();
  await expect(page.getByLabel("元素字体系列")).toBeVisible();
  await expect(page.getByLabel("元素文字颜色")).toBeVisible();
  await expect(page.getByLabel("元素字号")).toBeVisible();
  await expect(page.getByLabel("元素行高")).toBeVisible();
  await expect(page.getByLabel("元素下方间距")).toBeVisible();

  await page.getByLabel("元素字体系列").selectOption("Arial");
  await page.getByLabel("元素文字颜色").fill("#c0392b");
  await page.getByLabel("元素字号").press("ArrowRight");
  await page.getByLabel("元素下方间距").press("ArrowRight");

  await expect(schoolName).toHaveCSS("font-family", /Arial/);
  await expect(schoolName).toHaveCSS("color", "rgb(192, 57, 43)");
  await expect(page.getByText("9.5 pt", { exact: true })).toBeVisible();
  await expect(page.getByText("0.5 mm", { exact: true })).toBeVisible();

  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "导出 PDF" }).click();
  const download = await downloadPromise;
  const path = await download.path();
  expect(path).not.toBeNull();
  const bytes = await import("node:fs/promises").then((fs) => fs.readFile(path!));
  expect(bytes.subarray(0, 5).toString()).toBe("%PDF-");
  expect(bytes.byteLength).toBeGreaterThan(5_000);
});
