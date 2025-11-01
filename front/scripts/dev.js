#!/usr/bin/env node

// eslint-disable-next-line @typescript-eslint/no-require-imports
const { spawn } = require('child_process');
// eslint-disable-next-line @typescript-eslint/no-require-imports
const config = require('../server.config.js');

const PORT = config.port || 3000;

console.log(`Starting Next.js on port ${PORT}...`);

const child = spawn('next', ['dev', '-p', PORT.toString()], {
  stdio: 'inherit',
  shell: true
});

child.on('error', (error) => {
  console.error(`Error: ${error.message}`);
  process.exit(1);
});

child.on('exit', (code) => {
  process.exit(code || 0);
});
