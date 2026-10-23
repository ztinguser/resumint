import{expect,test}from"@playwright/test";

test("login preserves guest work and manual save handles concurrent edits, conflicts, favorites and expiry",async({page})=>{
  await page.goto("/editor?template=general-verdant");
  await expect(page.getByRole("button",{name:"导出 PDF"})).toBeEnabled();
  const name=page.getByRole("textbox",{name:"姓名，双击编辑"});
  const editName=async(value:string)=>{await name.dblclick();await name.fill(value);await name.press("Enter");};
  await editName("登录前内容");
  await page.getByRole("button",{name:"保存",exact:true}).click();
  await page.getByLabel("登录手机号").fill("13400134002");
  await page.getByRole("button",{name:"获取验证码"}).click();
  await page.getByLabel("短信验证码").fill("123456");
  await page.getByRole("button",{name:"登录 / 首次注册"}).click();
  await expect(name).toHaveText("登录前内容");
  await expect(page.getByRole("button",{name:"账号菜单"})).toHaveText("+86 134****4002");

  let releaseResponse:()=>void=()=>{};
  const gate=new Promise<void>(resolve=>{releaseResponse=resolve;});
  let delayed=true;
  await page.route("**/api/resumes",async route=>{if(route.request().method()==="POST"&&delayed){delayed=false;const response=await route.fetch();await gate;await route.fulfill({response});}else await route.continue();});
  await page.getByRole("button",{name:"保存",exact:true}).click();
  await expect(page.getByText("保存中…",{exact:true})).toBeVisible();
  await editName("保存期间继续编辑");
  releaseResponse();
  await expect(page.getByText("有未保存更改",{exact:true})).toBeVisible();
  await page.getByRole("button",{name:"保存",exact:true}).click();
  await expect(page.getByText("已保存",{exact:true})).toBeVisible();

  const draft=await page.evaluate(async()=>{const response=await fetch("/api/resumes");const rows=await response.json();return rows[0];});
  await page.evaluate(async({id,revision})=>{const token=document.cookie.split("; ").find(value=>value.startsWith("XSRF-TOKEN="))!.split("=")[1];const current=await fetch(`/api/resumes/${id}`).then(value=>value.json());current.document.profile.fullName="服务器并发版本";await fetch(`/api/resumes/${id}`,{method:"PUT",headers:{"Content-Type":"application/json","X-XSRF-TOKEN":decodeURIComponent(token)},body:JSON.stringify({baseRevision:revision,title:current.title,document:current.document})});},{id:draft.id,revision:draft.revision});
  await editName("本地冲突版本");
  await page.getByRole("button",{name:"保存",exact:true}).click();
  await expect(page.getByRole("dialog",{name:"版本冲突"})).toBeVisible();
  await expect(page.getByRole("button",{name:"重新加载服务器版本"})).toBeVisible();
  await expect(page.getByRole("button",{name:"本地另存为新草稿"})).toBeVisible();
  await page.getByRole("button",{name:"查看差异并人工合并"}).click();
  await expect(page.getByText("服务器并发版本")).toBeVisible();
  await page.getByRole("button",{name:"本地另存为新草稿"}).click();
  await expect(page.getByText("已保存",{exact:true})).toBeVisible();

  await page.goto("/templates");
  await expect(page.getByRole("link",{name:"+86 134****4002"})).toBeVisible();
  await page.getByRole("button",{name:"收藏模板"}).first().click();
  await expect(page.getByRole("button",{name:"取消收藏"}).first()).toBeVisible();
  await page.goto("/account");
  await expect(page.getByRole("heading",{name:"我的简历"})).toBeVisible();
  await expect(page.locator(".draft-list article")).toHaveCount(2);

  await page.goto(`/editor?template=general-verdant&draft=${draft.id}`);
  await expect(name).toHaveText("服务器并发版本");
  await page.context().clearCookies({name:"resumint_session"});
  await editName("会话过期仍保留");
  await page.getByRole("button",{name:"保存",exact:true}).click();
  await expect(page.getByText("会话已过期，当前编辑内容仍在；重新登录后请再次手动保存。")).toBeVisible();
  await expect(name).toHaveText("会话过期仍保留");
});
