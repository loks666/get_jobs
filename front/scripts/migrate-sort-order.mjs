// Migrate boss_option: add sort_order column and set city display order
// Usage: pnpm exec node scripts/migrate-sort-order.mjs

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import initSqlJs from 'sql.js';
import { createRequire } from 'module';

const log = (...args) => console.log('[migrate-sort-order]', ...args);

async function main() {
  try {
    const __filename = fileURLToPath(import.meta.url);
    const __dirname = path.dirname(__filename);
    const projectRoot = path.resolve(__dirname, '..', '..');
    const dbPath = path.resolve(projectRoot, 'db', 'getjobs.db');

    if (!fs.existsSync(dbPath)) {
      throw new Error(`Database file not found: ${dbPath}`);
    }

    const require = createRequire(import.meta.url);
    const wasmDir = path.dirname(require.resolve('sql.js/dist/sql-wasm.wasm'));
    const SQL = await initSqlJs({ locateFile: (file) => path.join(wasmDir, file) });

    const fileBuffer = fs.readFileSync(dbPath);
    const u8 = new Uint8Array(fileBuffer);
    const db = new SQL.Database(u8);

    const hasColumn = (table, column) => {
      const res = db.exec(`PRAGMA table_info(${table});`);
      if (!res || res.length === 0) return false;
      const names = res[0].values.map((row) => String(row[1]).toLowerCase());
      return names.includes(String(column).toLowerCase());
    };

    // Ensure boss_option exists
    const tables = db.exec("SELECT name FROM sqlite_master WHERE type='table' AND name='boss_option';");
    if (!tables || tables.length === 0 || tables[0].values.length === 0) {
      throw new Error("Table 'boss_option' not found in database.");
    }

    // Add sort_order column if missing
    if (!hasColumn('boss_option', 'sort_order')) {
      log('Adding column sort_order to boss_option ...');
      db.exec('ALTER TABLE boss_option ADD COLUMN sort_order INTEGER;');
    } else {
      log('Column sort_order already exists.');
    }

    // Reset sort_order for cities
    db.exec("UPDATE boss_option SET sort_order = NULL WHERE type='city';");

    // Define preferred city display order (as requested)
    const cityOrder = [
      '全国', '北京', '上海', '广州', '深圳',
      '杭州', '天津', '西安', '苏州', '武汉',
      '厦门', '长沙', '成都', '郑州', '重庆'
    ];

    const stmt = db.prepare("UPDATE boss_option SET sort_order = ? WHERE type='city' AND name = ?;");
    let updated = 0;
    cityOrder.forEach((name, idx) => {
      stmt.run([idx + 1, name]);
      updated += 1;
    });
    stmt.free();

    log(`Applied sort_order to ${updated} city rows.`);

    // Persist changes back to file
    const out = db.export();
    fs.writeFileSync(dbPath, Buffer.from(out));
    log('Database updated:', dbPath);

    // Optional: verify a few rows
    const verify = db.exec("SELECT id, name, sort_order FROM boss_option WHERE type='city' AND sort_order IS NOT NULL ORDER BY sort_order ASC LIMIT 10;");
    if (verify && verify.length > 0) {
      const rows = verify[0].values.map((r) => ({ id: r[0], name: r[1], sort_order: r[2] }));
      log('Top cities after migration:', rows);
    }

    db.close();
    log('Migration completed successfully.');
  } catch (err) {
    console.error('[migrate-sort-order] Migration failed:', err);
    process.exitCode = 1;
  }
}

await main();