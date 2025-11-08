import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

// 在 Next.js 配置了 `output: 'export'` 的情况下，生产环境应以静态方式服务 `dist`
// 因此这里改为使用 `serve` 直接托管 `dist` 目录，避免误用 `next start` 读取旧的 `.next` 构建

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const require = createRequire(import.meta.url);

const configPath = path.join(__dirname, 'server.config.js');
delete require.cache[require.resolve(configPath)];
const config = require(configPath);

// 获取生产环境配置
const port = (config.production && config.production.port) ? config.production.port : (config.port || 8080);
const hostname = (config.production && config.production.hostname) ? config.production.hostname : '0.0.0.0';

// 使用 `serve` 静态托管 dist 目录
const serveProcess = spawn('npx', ['serve', '-l', port.toString(), 'dist'], {
  stdio: 'inherit',
  shell: true,
  env: {
    ...process.env,
    HOSTNAME: hostname,
  }
});

serveProcess.on('error', (error) => {
  console.error('启动静态服务失败:', error);
  process.exit(1);
});

serveProcess.on('close', (code) => {
  console.log(`静态服务退出，代码: ${code}`);
  process.exit(code);
});