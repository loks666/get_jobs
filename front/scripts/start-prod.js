const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

// 读取服务器配置
const serverConfig = require('../server.config.js');

// 生成 .env.local 文件
const envPath = path.join(__dirname, '../.env.local');
const port = serverConfig.production.port;
const hostname = serverConfig.production.hostname;

const envContent = `# 这个文件由启动脚本自动生成，请不要手动修改
PORT=${port}
HOSTNAME=${hostname}
API_BASE_URL=${serverConfig.api.baseUrl}
APP_NAME=${serverConfig.app.name}
APP_VERSION=${serverConfig.app.version}
`;

// 写入环境变量文件
fs.writeFileSync(envPath, envContent);

console.log(`🔧 已从 server.config.js 加载生产配置:`);
console.log(`   端口: ${port}`);
console.log(`   主机: ${hostname}`);
console.log(`   API地址: ${serverConfig.api.baseUrl}`);

// 启动 Next.js，直接传递端口参数
const nextProcess = spawn('next', ['start', '-p', port.toString()], {
  stdio: 'inherit',
  shell: true,
  env: {
    ...process.env,
    PORT: port.toString(),
    HOSTNAME: hostname
  }
});

nextProcess.on('close', (code) => {
  console.log(`Next.js 进程退出，代码: ${code}`);
});