"use client";
import {useState} from "react";
import {ApiError,apiFetch,type CurrentUser} from "./auth-client";

type Mode="sms"|"password"|"reset";

export function LoginBubble({open,onClose,onLogin}:{open:boolean;onClose:()=>void;onLogin:(user:CurrentUser)=>void}){
  const[mode,setMode]=useState<Mode>("sms");
  const[phone,setPhone]=useState("");
  const[password,setPassword]=useState("");
  const[newPassword,setNewPassword]=useState("");
  const[code,setCode]=useState("");
  const[attemptId,setAttemptId]=useState("");
  const[busy,setBusy]=useState(false);
  const[message,setMessage]=useState("");
  if(!open)return null;

  const switchMode=(next:Mode)=>{setMode(next);setAttemptId("");setCode("");setMessage("");};
  const run=async(action:()=>Promise<CurrentUser>)=>{setBusy(true);setMessage("");try{const user=await action();onLogin(user);onClose();}catch(error){setMessage(error instanceof Error?error.message:"登录失败");}finally{setBusy(false);}};
  const send=async()=>{setBusy(true);setMessage("");try{const result=await apiFetch<{attemptId:string;maskedPhone:string}>("/api/auth/sms-code",{method:"POST",body:JSON.stringify({phone,purpose:mode==="reset"?"PASSWORD_RESET":"LOGIN"})});setAttemptId(result.attemptId);setMessage(`验证码已发送至 ${result.maskedPhone}`);}catch(error){setMessage(error instanceof ApiError?error.message:"验证码发送失败");}finally{setBusy(false);}};
  const reset=async()=>{setBusy(true);setMessage("");try{await apiFetch("/api/auth/password-reset",{method:"POST",body:JSON.stringify({attemptId,phone,code,newPassword})});setPassword("");setNewPassword("");setCode("");setAttemptId("");setMode("password");setMessage("密码已重置，请使用新密码登录");}catch(error){setMessage(error instanceof Error?error.message:"密码重置失败");}finally{setBusy(false);}};

  return <section className="login-bubble" aria-label="登录账号">
    <div className="bubble-title"><div><strong>{mode==="reset"?"重置密码":"登录后手动保存"}</strong><small>{mode==="reset"?"仅可信设备可通过短信重置":"登录不会离开页面或清空当前内容"}</small></div><button aria-label="关闭登录" onClick={onClose}>×</button></div>
    <div className="login-tabs"><button className={mode==="sms"?"active":""} onClick={()=>switchMode("sms")}>短信登录</button><button className={mode==="password"?"active":""} onClick={()=>switchMode("password")}>密码登录</button></div>
    <label>+86 手机号<input aria-label="登录手机号" value={phone} inputMode="tel" onChange={event=>setPhone(event.target.value)}/></label>
    {mode==="sms"&&<><div className="code-row"><input aria-label="短信验证码" placeholder="验证码" value={code} onChange={event=>setCode(event.target.value)}/><button disabled={busy||!phone} onClick={send}>{attemptId?"重新发送":"获取验证码"}</button></div><button className="login-submit" disabled={busy||!attemptId||!code} onClick={()=>run(()=>apiFetch<CurrentUser>("/api/auth/sms-login",{method:"POST",body:JSON.stringify({attemptId,phone,code})}))}>登录 / 首次注册</button></>}
    {mode==="password"&&<><label>密码<input aria-label="登录密码" type="password" value={password} onChange={event=>setPassword(event.target.value)}/></label><button className="login-submit" disabled={busy||!phone||!password} onClick={()=>run(()=>apiFetch<CurrentUser>("/api/auth/password-login",{method:"POST",body:JSON.stringify({phone,password})}))}>密码登录</button><button className="text-button" onClick={()=>switchMode("reset")}>忘记密码</button></>}
    {mode==="reset"&&<><div className="code-row"><input aria-label="重置验证码" placeholder="验证码" value={code} onChange={event=>setCode(event.target.value)}/><button disabled={busy||!phone} onClick={send}>{attemptId?"重新发送":"获取验证码"}</button></div><label>新密码<input aria-label="重置后的新密码" type="password" minLength={6} maxLength={72} value={newPassword} onChange={event=>setNewPassword(event.target.value)} placeholder="6–72 位，含字母和数字"/></label><button className="login-submit" disabled={busy||!attemptId||!code||!newPassword} onClick={()=>void reset()}>重置密码</button><button className="text-button" onClick={()=>switchMode("password")}>返回密码登录</button></>}
    {message&&<p className="login-message" role="status">{message}</p>}
  </section>;
}
