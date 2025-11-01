#!/usr/bin/env node

// 从 server.config.js 读取配置并设置环境变量，然后启动 Next.js
const config = require('../server.config.js');
process.env.PORT = config.port || 3000;

// 直接执行 next dev
require('child_process').spawn('next', ['dev'], {
  stdio: 'inherit',
  shell: true,
  env: process.env
});
