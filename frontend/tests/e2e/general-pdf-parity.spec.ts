import {expect,test,type Page} from "@playwright/test";

type Geometry={border:string;margin:string;sectionPadding:string;sectionGap:string};

async function geometry(page:Page):Promise<Geometry>{
  return page.locator(".general-template-page").first().evaluate(element=>{
    const header=getComputedStyle(element.querySelector(".general-header")!);
    const section=getComputedStyle(element.querySelector(".general-section, .general-print-blocks .general-section-title")!);
    const items=element.querySelector(".general-section-items");
    return {border:header.borderBottom,margin:header.marginBottom,sectionPadding:section.paddingTop,sectionGap:items?getComputedStyle(items).gap:""};
  });
}

test("general PDF uses the same divider and vertical rhythm as the workbench",async({page})=>{
  await page.goto("/editor?template=general-verdant&qa=pdf-parity");
  const workbench=await geometry(page);
  await page.goto("/print?fixture=general-default");
  await page.emulateMedia({media:"print"});
  await expect(page.locator(".general-template-page")).toHaveCount(1);
  expect(await geometry(page)).toEqual(workbench);
});
