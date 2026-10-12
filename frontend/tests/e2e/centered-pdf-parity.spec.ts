import{expect,test,type Page}from"@playwright/test";

type Geometry={padding:string;headerHeight:string;columns:string;gap:string;nameSize:string};

async function geometry(page:Page):Promise<Geometry>{
  return page.locator(".centered-template-page").first().evaluate(element=>{
    const pageStyle=getComputedStyle(element);
    const header=getComputedStyle(element.querySelector(".centered-header")!);
    const columns=getComputedStyle(element.querySelector(".centered-columns")!);
    const name=getComputedStyle(element.querySelector(".centered-name")!);
    return {padding:pageStyle.padding,headerHeight:header.height,columns:columns.gridTemplateColumns,gap:columns.columnGap,nameSize:name.fontSize};
  });
}

test("second candidate print view preserves the workbench composition",async({page})=>{
  await page.goto("/editor?template=business-ledger&qa=centered-pdf-parity");
  const workbench=await geometry(page);
  await page.goto("/print?fixture=centered-default");
  await page.emulateMedia({media:"print"});
  await expect(page.locator(".centered-template-page")).toHaveCount(1);
  await expect(page.getByText("林清远",{exact:true})).toBeVisible();
  await expect(page.getByText("主要技能",{exact:true})).toBeVisible();
  expect(await geometry(page)).toEqual(workbench);
  await page.locator(".centered-template-page").screenshot({path:"test-results/candidates/centered-print.png"});
});
