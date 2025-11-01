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
  
  // 生产环境配置
  ...(process.env.NODE_ENV === 'production' && {
    output: 'standalone',
  }),
};

export default nextConfig;
