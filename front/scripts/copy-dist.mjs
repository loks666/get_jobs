import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

// 获取当前文件的目录路径
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 源目录和目标目录
const sourceDir = path.join(__dirname, '..', 'dist');
const targetDir = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'dist');

console.log('开始复制前端构建文件...');
console.log('源目录:', sourceDir);
console.log('目标目录:', targetDir);

// 递归删除目录
function deleteFolderRecursive(dirPath) {
  if (fs.existsSync(dirPath)) {
    fs.readdirSync(dirPath).forEach((file) => {
      const curPath = path.join(dirPath, file);
      if (fs.lstatSync(curPath).isDirectory()) {
        deleteFolderRecursive(curPath);
      } else {
        fs.unlinkSync(curPath);
      }
    });
    fs.rmdirSync(dirPath);
  }
}

// 递归复制目录
function copyFolderRecursive(source, target) {
  // 创建目标目录
  if (!fs.existsSync(target)) {
    fs.mkdirSync(target, { recursive: true });
  }

  // 读取源目录
  const files = fs.readdirSync(source);

  files.forEach((file) => {
    const sourcePath = path.join(source, file);
    const targetPath = path.join(target, file);

    if (fs.lstatSync(sourcePath).isDirectory()) {
      // 递归复制子目录
      copyFolderRecursive(sourcePath, targetPath);
    } else {
      // 复制文件
      fs.copyFileSync(sourcePath, targetPath);
    }
  });
}

try {
  // 检查源目录是否存在
  if (!fs.existsSync(sourceDir)) {
    console.error('错误: dist目录不存在，请先运行 npm run build');
    process.exit(1);
  }

  // 删除旧的目标目录
  if (fs.existsSync(targetDir)) {
    console.log('删除旧的目标目录...');
    deleteFolderRecursive(targetDir);
  }

  // 复制文件
  console.log('复制文件...');
  copyFolderRecursive(sourceDir, targetDir);

  console.log('✅ 构建文件复制成功!');
  console.log(`文件已复制到: ${targetDir}`);
} catch (error) {
  console.error('❌ 复制失败:', error.message);
  process.exit(1);
}
