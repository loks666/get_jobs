import type { NextConfig } from "next";

// 读取服务器配置
const serverConfig = require('./server.config.js');

const nextConfig: NextConfig = {
  // 将API配置暴露给客户端
  env: {
    API_BASE_URL: serverConfig.api.baseUrl,
    APP_NAME: serverConfig.app.name,
    APP_VERSION: serverConfig.app.version,
  },

  // 静态导出配置
  output: 'export',
  // 输出目录为dist
  distDir: 'dist',
  // 禁用图片优化（静态导出不支持）
  images: {
    unoptimized: true,
  },
};

export default nextConfig;
