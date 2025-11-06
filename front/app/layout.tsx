import type { Metadata } from "next";
import "./globals.css";
import Sidebar from "./components/Sidebar";
import ContentArea from "./components/ContentArea";

export const metadata: Metadata = {
  title: "Get Jobs - 配置管理中心",
  description: "配置管理中心，管理application.yaml和环境变量配置",
  icons: {
    icon: {
      url: "data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>🍀</text></svg>",
      type: "image/svg+xml",
    },
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body suppressHydrationWarning>
        <div className="flex min-h-screen">
          <Sidebar />
          <ContentArea>
            {children}
          </ContentArea>
        </div>
      </body>
    </html>
  );
}
