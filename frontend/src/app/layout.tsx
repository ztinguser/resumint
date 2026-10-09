import type { Metadata } from "next";
import "./globals.css";
import "./print/print.css";

export const metadata: Metadata = {
  title: "Resumint · 在线简历编辑器",
  description: "选择模板，填写内容并导出清晰的 PDF 简历",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
