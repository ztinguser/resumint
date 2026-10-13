import{expect,test,type Page}from"@playwright/test";

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
