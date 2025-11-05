#!/usr/bin/env node

// 从 server.config.js 读取配置并设置环境变量，然后启动 Next.js 生产服务器
const config = require('../server.config.js');
process.env.PORT = config.production?.port || config.port || 3000;
process.env.HOSTNAME = config.production?.hostname || 'localhost';

// 直接执行 next start
require('child_process').spawn('next', ['start'], {
  stdio: 'inherit',
  shell: true,
  env: process.env
});