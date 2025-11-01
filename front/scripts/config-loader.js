#!/usr/bin/env node

const { spawn } = require('child_process');
const path = require('path');

// 读取服务器配置
const serverConfig = require('../server.config.js');

// 获取命令行参数
const command = process.argv[2]; // 'dev' 或 'start'

let nextCommand, args;

if (command === 'dev') {
  nextCommand = 'next';
  args = ['dev', '-p', serverConfig.port.toString()];
  
  // 如果启用了 turbo，添加 --turbo 参数
  if (serverConfig.development.turbo) {
    args.push('--turbo');
  }
  
  console.log(`🚀 启动开发服务器，端口: ${serverConfig.port}`);
} else if (command === 'start') {
  nextCommand = 'next';
  args = [
    'start', 
    '-p', serverConfig.production.port.toString(),
    '-H', serverConfig.production.hostname
  ];
  
  console.log(`🚀 启动生产服务器，端口: ${serverConfig.production.port}，主机: ${serverConfig.production.hostname}`);
} else {
  console.error('❌ 请指定命令: dev 或 start');
  process.exit(1);
}

// 设置环境变量
process.env.PORT = command === 'dev' ? serverConfig.port : serverConfig.production.port;
process.env.HOSTNAME = command === 'start' ? serverConfig.production.hostname : 'localhost';

// 启动 Next.js
const child = spawn(nextCommand, args, {
  stdio: 'inherit',
  shell: process.platform === 'win32'
});

// 处理进程退出
child.on('close', (code) => {
  process.exit(code);
});

// 处理中断信号
process.on('SIGINT', () => {
  child.kill('SIGINT');
});

process.on('SIGTERM', () => {
  child.kill('SIGTERM');
});