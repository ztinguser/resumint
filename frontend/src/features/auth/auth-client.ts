export type CurrentUser={userId:string;maskedPhone:string;avatar:"DEFAULT"|"MALE"|"FEMALE";passwordSet:boolean;role:"USER"|"ADMIN"};
export class ApiError extends Error{constructor(public status:number,public code:string,message:string,public details:Record<string,unknown>={}){super(message);}}
const csrf=()=>document.cookie.split("; ").find((value)=>value.startsWith("XSRF-TOKEN="))?.split("=").slice(1).join("=");
export async function apiFetch<T>(input:string,init:RequestInit={}):Promise<T>{
  const token=typeof document==="undefined"?undefined:csrf();
  const headers=new Headers(init.headers);if(init.body&&!headers.has("Content-Type"))headers.set("Content-Type","application/json");
  if(token&&!headers.has("X-XSRF-TOKEN"))headers.set("X-XSRF-TOKEN",decodeURIComponent(token));
  const response=await fetch(input,{...init,headers,credentials:"same-origin",cache:"no-store"});
  if(!response.ok){const payload=await response.json().catch(()=>({}));throw new ApiError(response.status,payload.code??"REQUEST_FAILED",payload.message??"请求失败",payload.details??{});}
  if(response.status===204)return undefined as T;return response.json() as Promise<T>;
}
export async function currentUser(){try{return await apiFetch<CurrentUser>("/api/me");}catch(error){if(error instanceof ApiError&&error.status===401)return null;throw error;}}
