import{expect,test,type Page}from"@playwright/test";
import{createBarelineDocument}from"../../src/resume/templates/bareline";

async function geometry(page:Page){
  return page.locator(".bareline-template-page").first().evaluate(element=>{
    const pageStyle=getComputedStyle(element);
    const header=getComputedStyle(element.querySelector(".bareline-header")!);
    const columns=getComputedStyle(element.querySelector(".bareline-columns")!);
    const name=getComputedStyle(element.querySelector(".bareline-name")!);
    return {padding:pageStyle.padding,background:pageStyle.backgroundColor,headerHeight:header.height,columns:columns.gridTemplateColumns,gap:columns.columnGap,nameSize:name.fontSize};
  });
}

test("third candidate print view preserves the workbench composition",async({page})=>{
  await page.goto("/editor?template=minimal-bareline&qa=bareline-pdf-parity");
  const workbench=await geometry(page);
  await page.goto("/print?fixture=bareline-default");
  await page.emulateMedia({media:"print"});
  await expect(page.locator(".bareline-template-page")).toHaveCount(1);
  await expect(page.getByText("林清远，",{exact:true})).toBeVisible();
  await expect(page.getByText("工作经历",{exact:true})).toBeVisible();
  expect(await geometry(page)).toEqual(workbench);
  await page.locator(".bareline-template-page").screenshot({path:"test-results/candidates/bareline-print.png"});
});

test("added education keeps the configured PDF bottom margin",async({page})=>{
  const document=createBarelineDocument();
  const education=document.sections.find(section=>section.id==="bareline-education")!;
  const source=education.items[0];
  education.items.push({...structuredClone(source),id:"bareline-education-added",title:"江南大学（新增）"});

  await page.goto("/print?fixture=bareline-default");
  await page.waitForFunction(()=>window.__RESUMINT_READY__===true);
  await page.evaluate(next=>window.renderResume?.(next),document);
  await page.waitForFunction(()=>window.__RESUMINT_READY__===true);
  await page.emulateMedia({media:"print"});

  const margins=await page.locator(".bareline-template-page").evaluateAll(pages=>pages.map(element=>{
    const pageRect=element.getBoundingClientRect();
    const paddingBottom=parseFloat(getComputedStyle(element).paddingBottom);
    const content=[...element.querySelectorAll<HTMLElement>(".bareline-header,.bareline-continuation,.bareline-contacts,.general-section-title,.general-entry-title,.general-entry-subtitle,.general-entry-date,.general-entry-summary")];
    const contentBottom=Math.max(...content.map(node=>node.getBoundingClientRect().bottom));
    return {actual:pageRect.bottom-contentBottom,required:paddingBottom};
  }));
  for(const margin of margins)expect(margin.actual).toBeGreaterThanOrEqual(margin.required-1);
  await expect(page.locator('[data-page="1"]')).not.toContainText("江南大学（新增）");
  await expect(page.locator('[data-page="2"]')).toContainText("江南大学（新增）");
  await expect(page.locator('[data-page="2"]')).toContainText("主修交互设计、用户研究");
  await page.locator(".print-root").screenshot({path:"test-results/candidates/bareline-added-education-print.png"});
});
