import{expect,test}from"@playwright/test";

test("password login and reset, draft rename, and two-phone change complete in the account space",async({page})=>{
  const oldPhone="13200132000";
  const newPhone="13100131000";
  await page.goto("/editor?template=general-verdant");
  await expect(page.getByRole("button",{name:"导出 PDF"})).toBeEnabled();
  await page.getByRole("button",{name:"账号菜单"}).click();
  await page.getByLabel("登录手机号").fill(oldPhone);
  await page.getByRole("button",{name:"获取验证码"}).click();
  await page.getByLabel("短信验证码").fill("123456");
  await page.getByRole("button",{name:"登录 / 首次注册"}).click();
  await expect(page.getByRole("button",{name:"账号菜单"})).toHaveText("+86 132****2000");
  await page.getByRole("button",{name:"保存",exact:true}).click();
  await expect(page.getByText("已保存",{exact:true})).toBeVisible();

  await page.goto("/account");
  await expect(page.locator(".draft-list article")).toHaveCount(1);
  page.once("dialog",dialog=>dialog.accept("安全验证简历"));
  await page.getByRole("button",{name:"重命名"}).click();
  await expect(page.getByText("安全验证简历",{exact:true})).toBeVisible();

  await page.getByRole("button",{name:"账号与安全"}).click();
  await page.getByLabel("设置密码").fill("original123");
  await page.getByRole("button",{name:"保存密码"}).click();
  await expect(page.getByText("密码已更新",{exact:true})).toBeVisible();

  await page.getByLabel("当前密码").fill("original123");
  await page.getByLabel("当前完整手机号").fill(oldPhone);
  await page.getByRole("button",{name:"发送旧号验证码"}).click();
  await page.getByLabel("旧号验证码").fill("123456");
  await page.getByLabel("新手机号").fill(newPhone);
  await page.getByRole("button",{name:"发送新号验证码"}).click();
  await page.getByLabel("新号验证码").fill("123456");
  await page.getByRole("button",{name:"确认换绑"}).click();
  await expect(page.getByText("手机号已换绑，其他设备会话和可信设备已失效",{exact:true})).toBeVisible();
  await expect(page.getByText("+86 131****1000",{exact:true}).first()).toBeVisible();

  await page.getByRole("button",{name:"退出登录"}).click();
  await page.getByRole("button",{name:"密码登录"}).first().click();
  await page.getByLabel("登录手机号").fill(newPhone);
  await page.getByLabel("登录密码").fill("original123");
  await page.getByRole("button",{name:"密码登录",exact:true}).last().click();
  await expect(page.getByText("+86 131****1000",{exact:true}).first()).toBeVisible();

  await page.getByRole("button",{name:"退出登录"}).click();
  await page.getByRole("button",{name:"密码登录"}).first().click();
  await page.getByRole("button",{name:"忘记密码"}).click();
  await page.getByLabel("登录手机号").fill(newPhone);
  await page.getByRole("button",{name:"获取验证码"}).click();
  await page.getByLabel("重置验证码").fill("123456");
  await page.getByLabel("重置后的新密码").fill("changed456");
  await page.getByRole("button",{name:"重置密码"}).click();
  await expect(page.getByText("密码已重置，请使用新密码登录",{exact:true})).toBeVisible();
  await page.getByLabel("登录密码").fill("original123");
  await page.getByRole("button",{name:"密码登录",exact:true}).last().click();
  await expect(page.getByText("手机号或密码错误",{exact:true})).toBeVisible();
  await page.getByLabel("登录密码").fill("changed456");
  await page.getByRole("button",{name:"密码登录",exact:true}).last().click();
  await expect(page.getByText("+86 131****1000",{exact:true}).first()).toBeVisible();

  await page.getByRole("button",{name:"账号与安全"}).click();
  await page.getByLabel("当前密码").fill("changed456");
  await page.getByLabel("删除账号确认").fill("DELETE");
  page.once("dialog",dialog=>dialog.accept());
  await page.getByRole("button",{name:"永久删除账号"}).click();
  await expect(page.getByText("账号及关联数据已删除",{exact:true})).toBeVisible();
  await page.getByRole("button",{name:"密码登录"}).first().click();
  await page.getByLabel("登录手机号").fill(newPhone);
  await page.getByLabel("登录密码").fill("changed456");
  await page.getByRole("button",{name:"密码登录",exact:true}).last().click();
  await expect(page.getByText("手机号或密码错误",{exact:true})).toBeVisible();
});
