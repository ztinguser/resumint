import { expect, test } from "@playwright/test";

test("general template supports module duplication, consistent contacts, and element styling", async ({ page }) => {
  await page.goto("/editor?template=general-verdant&qa=direct-editor");
  await expect(page.locator(".general-section.is-selected")).toHaveCount(0, { timeout: 2_000 });

  const profileTitle = page.getByRole("textbox", { name: "个人简介标题，双击编辑" });
  const contactTitle = page.getByRole("textbox", { name: "联系方式标题，双击编辑" });
  await expect(profileTitle).toHaveCount(1, { timeout: 2_000 });
  await expect(contactTitle).toHaveCount(1, { timeout: 2_000 });
  await profileTitle.dblclick();
  await profileTitle.fill("关于我");
  await profileTitle.press("Enter");
  await contactTitle.dblclick();
  await contactTitle.fill("联系我");
  await contactTitle.press("Enter");
  await expect(profileTitle).toHaveText("关于我");
  await expect(contactTitle).toHaveText("联系我");

  const initialEducationSection = page.locator('[data-section-id="general-education"]');
  await page.getByRole("button", { name: "拖动教育经历排序" }).click();
  await expect(initialEducationSection).toHaveClass(/is-selected/);
  await page.locator(".general-avatar").click();
  await expect(page.locator(".general-section.is-selected")).toHaveCount(0);

  await page.getByRole("button", { name: "复制模块 工作经历" }).click();
  await expect(page.locator(".general-section-title")).toHaveText([
    "教育经历",
    "工作经历",
    "工作经历",
    "个人总结",
  ]);
  const duplicatedWorkSection=page.locator(".general-section").nth(2);
  await expect(duplicatedWorkSection).toContainText("星海科技");
  await expect(duplicatedWorkSection).toContainText("有光互动");
  await expect(page.getByRole("menuitem")).toHaveCount(0);

  const contacts = page.locator(".contact-list-wrap li");
  await expect(contacts).toHaveCount(4);
  await page.getByRole("button", { name: "新增联系方式" }).click();
  await expect(contacts).toHaveCount(5);
  await expect(contacts.last().getByRole("textbox", { name: "联系方式，双击编辑" })).toHaveText("双击填写");
  await expect(contacts.last()).not.toContainText("其他");
  await page.getByRole("button", { name: "删除联系方式 双击填写" }).click();
  await expect(contacts).toHaveCount(4);
  await page.getByRole("button", { name: "撤销" }).click();
  await expect(contacts).toHaveCount(5);

  await page.getByRole("button", { name: "删除条目 江南大学" }).click();
  await expect(page.getByText("江南大学", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "撤销" }).click();
  await expect(page.getByText("江南大学", { exact: true })).toHaveCount(1);

  await duplicatedWorkSection.getByRole("button", { name: "删除模块 工作经历" }).click();
  await expect(page.locator(".general-section-title")).toHaveText(["教育经历", "工作经历", "个人总结"]);
  await page.getByRole("button", { name: "撤销" }).click();
  await expect(page.locator(".general-section-title")).toHaveText(["教育经历", "工作经历", "工作经历", "个人总结"]);

  const schoolName = page.locator('[data-element-id="item.general-education-1.title"]');
  await schoolName.click();
  const educationSection = page.locator('[data-section-id="general-education"]');
  const educationItem = page.locator('[data-item-id="general-education-1"]');
  await expect(educationSection).not.toHaveClass(/is-selected/);
  await expect(educationSection).toHaveClass(/has-selected-descendant/);
  await expect(educationSection.getByRole("button", { name: "复制模块 教育经历" })).toHaveCSS("opacity", "0");
  await expect(educationSection.getByRole("button", { name: "删除模块 教育经历" })).toHaveCSS("opacity", "0");
  await expect(educationItem).toHaveClass(/is-selected/);
  await expect(educationItem.getByRole("button", { name: "在江南大学后新增一项" })).toHaveCSS("opacity", "1");
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

  const summaryItemTitle=page.locator('[data-element-id="item.general-summary-copy.title"]');
  await summaryItemTitle.click();
  await page.getByRole("button", { name: "删除元素 条目名称" }).click();
  await expect(summaryItemTitle).toHaveCount(0);
  await page.getByRole("button", { name: "撤销" }).click();
  await expect(summaryItemTitle).toHaveText("总结");

  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "导出 PDF" }).click();
  const download = await downloadPromise;
  const path = await download.path();
  expect(path).not.toBeNull();
  const bytes = await import("node:fs/promises").then((fs) => fs.readFile(path!));
  expect(bytes.subarray(0, 5).toString()).toBe("%PDF-");
  expect(bytes.byteLength).toBeGreaterThan(5_000);
});
