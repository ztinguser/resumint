import { expect, test } from "@playwright/test";
import { benchmarkFixtures } from "../fixtures/benchmark-fixtures";

for (const fixture of benchmarkFixtures) {
  test(`${fixture.id} stays inside A4 pages`, async ({ page }) => {
    await page.goto(`/print?fixture=${fixture.id}`);
    await page.waitForFunction(() => window.__RESUMINT_READY__ === true);

    const pages = page.locator(".resume-page");
    await expect(pages).toHaveCount(fixture.expected.pages);
    await expect(page.locator(".print-root")).toHaveAttribute("data-issues", fixture.expected.issues.join(","));

    const text = await page.locator(".print-root").innerText();
    for (const expectedText of fixture.expected.text) expect(text).toContain(expectedText);

    const hrefs = await page.locator("a").evaluateAll((anchors) => anchors.map((anchor) => (anchor as HTMLAnchorElement).href));
    for (const expectedLink of fixture.expected.links) expect(hrefs).toContain(expectedLink);

    const audit = await pages.evaluateAll((pageNodes) => pageNodes.map((pageNode) => {
      const pageBox = pageNode.getBoundingClientRect();
      const elements = pageNode.querySelectorAll("header, .page-body, .page-body *, .decoration, .portrait-frame");
      const overflow = Array.from(elements).filter((element) => {
        const box = element.getBoundingClientRect();
        return box.left < pageBox.left - 1 || box.right > pageBox.right + 1 || box.top < pageBox.top - 1 || box.bottom > pageBox.bottom + 1;
      }).map((element) => `${element.tagName}.${element.className}`);
      return { overflow, text: (pageNode.textContent ?? "").trim() };
    }));
    expect(audit.every((result) => result.overflow.length === 0)).toBe(true);
    expect(audit.every((result) => result.text.length > 20)).toBe(true);

    await expect(page).toHaveScreenshot(`${fixture.id}.png`, {
      fullPage: true,
      animations: "disabled",
      maxDiffPixelRatio: fixture.expected.screenshotThreshold,
    });
  });
}
