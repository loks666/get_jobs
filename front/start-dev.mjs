import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

// 获取当前文件的目录路径
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 创建require函数来导入CommonJS模块
const require = createRequire(import.meta.url);

// 读取配置文件
const configPath = path.join(__dirname, 'server.config.js');
delete require.cache[require.resolve(configPath)];
const config = require(configPath);

// 获取开发环境配置
const port = config.port || 3000;
const hostname = config.development?.hostname || 'localhost';

// 启动Next.js开发服务器
const nextDev = spawn('npx', ['next', 'dev', '-p', port.toString(), '-H', hostname], {
  stdio: 'inherit',
  shell: true
});

nextDev.on('error', (error) => {
  console.error('启动失败:', error);
  process.exit(1);
});

nextDev.on('close', (code) => {
  process.exit(code);
});