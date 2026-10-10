import type {NextRequest} from "next/server";

export const dynamic="force-dynamic";

type RouteContext={params:Promise<{path:string[]}>};

async function proxy(request:NextRequest,context:RouteContext){
  const{path}=await context.params;
  const backendOrigin=process.env.BACKEND_ORIGIN??"http://127.0.0.1:8080";
  const target=new URL(`/api/${path.map(encodeURIComponent).join("/")}`,backendOrigin);
  target.search=request.nextUrl.search;

  const requestHeaders=new Headers(request.headers);
  requestHeaders.delete("connection");
  requestHeaders.delete("content-length");
  requestHeaders.delete("host");
  requestHeaders.set("x-forwarded-host",request.headers.get("host")??"");
  requestHeaders.set("x-forwarded-proto",request.nextUrl.protocol.replace(":",""));

  const upstream=await fetch(target,{
    method:request.method,
    headers:requestHeaders,
    body:request.method==="GET"||request.method==="HEAD"?undefined:await request.arrayBuffer(),
    cache:"no-store",
    redirect:"manual",
  });

  const responseHeaders=new Headers();
  upstream.headers.forEach((value,name)=>{
    if(!["connection","content-length","set-cookie","transfer-encoding"].includes(name.toLowerCase())){
      responseHeaders.append(name,value);
    }
  });
  for(const cookie of upstream.headers.getSetCookie())responseHeaders.append("set-cookie",cookie);
  return new Response(upstream.body,{status:upstream.status,headers:responseHeaders});
}

export{proxy as GET,proxy as POST,proxy as PUT,proxy as PATCH,proxy as DELETE};
