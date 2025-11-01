import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const require = createRequire(import.meta.url);

const configPath = path.join(__dirname, 'server.config.js');
delete require.cache[require.resolve(configPath)];
const config = require(configPath);

// 获取生产环境配置
const port = config.production.port || 8080;
const hostname = config.production.hostname || '0.0.0.0';

// 启动Next.js生产服务器
const nextStart = spawn('npx', ['next', 'start', '-p', port.toString(), '-H', hostname], {
  stdio: 'inherit',
  shell: true
});

nextStart.on('error', (error) => {
  console.error('启动失败:', error);
  process.exit(1);
});

nextStart.on('close', (code) => {
  process.exit(code);
});