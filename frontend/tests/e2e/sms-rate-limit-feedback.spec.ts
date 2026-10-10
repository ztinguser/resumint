import {expect,test} from "@playwright/test";

test("shows the SMS resend cooldown immediately after a successful send",async({page})=>{
  await page.route("**/api/auth/sms-code",async route=>{
    await route.fulfill({
      status:200,
      contentType:"application/json",
      body:JSON.stringify({attemptId:"attempt-1",maskedPhone:"138****8000",retryAfterSeconds:60}),
    });
  });

  await page.goto("/account");
  await page.getByRole("button",{name:"密码登录"}).first().click();
  await page.getByRole("button",{name:"忘记密码"}).click();
  await page.getByLabel("登录手机号").fill("13800138000");
  await page.getByRole("button",{name:"获取验证码"}).click();

  await expect(page.getByRole("button",{name:/秒后重试/})).toBeDisabled();
  await expect(page.getByRole("status")).toContainText("后可重新发送");
});

test("keeps the server retry window visible after a 429 response",async({page})=>{
  let sends=0;
  await page.route("**/api/auth/sms-code",async route=>{
    sends+=1;
    if(sends===1){
      await route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({attemptId:"attempt-1",maskedPhone:"138****8000",retryAfterSeconds:0})});
      return;
    }
    await route.fulfill({status:429,contentType:"application/json",body:JSON.stringify({code:"SMS_RATE_LIMITED",message:"验证码发送过于频繁",details:{retryAfterSeconds:12}})});
  });

  await page.goto("/account");
  await page.getByRole("button",{name:"密码登录"}).first().click();
  await page.getByRole("button",{name:"忘记密码"}).click();
  await page.getByLabel("登录手机号").fill("13800138000");
  await page.getByRole("button",{name:"获取验证码"}).click();
  await page.getByRole("button",{name:"重新发送"}).click();

  await expect(page.getByRole("status")).toHaveText("验证码发送过于频繁");
  await expect(page.getByRole("button",{name:/秒后重试/})).toBeDisabled();
});
