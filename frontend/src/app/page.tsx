import Link from "next/link";

export default function Home() {
  return <main className="home-page">
    <header><Link className="brand" href="/">Resumint</Link><Link href="/templates">浏览模板</Link></header>
    <section><span className="home-kicker">A RESUME, KEPT IN FLOW.</span><h1>内容会变，<br/><em>版式不乱。</em></h1><p>像填写表单一样整理经历，用同一份结构化内容预览并导出 A4 PDF。</p><Link className="home-cta" href="/templates">浏览模板 <span>→</span></Link></section>
    <div className="home-orbit" aria-hidden="true"><i/><b>R</b></div>
  </main>;
}
