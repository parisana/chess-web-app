import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';

// Keep k6's normal console output, but never copy setup_data to the saved report.
const args = process.argv.slice(2);
if (args[0] !== '--report' || !args[1] || !args[2]) {
  console.error('Usage: node run-k6.mjs --report results/name.json script.ts [k6 options]');
  process.exit(1);
}

const reportPath = args[1];
const k6Args = args.slice(2);
if (k6Args.some(arg => arg === '--summary-export' || arg.startsWith('--summary-export='))) {
  console.error('Use --report instead of passing --summary-export to this runner.');
  process.exit(1);
}

// A private temporary directory contains the raw report until sanitization.
const temporaryDirectory = mkdtempSync(join(tmpdir(), 'chess-k6-'));
const rawPath = join(temporaryDirectory, 'raw-summary.json');
let exitCode = 1;

try {
  const result = spawnSync('k6', ['run', '--summary-export', rawPath, ...k6Args], {
    stdio: 'inherit',
    env: process.env,
  });

  if (result.error) {
    throw new Error(`Could not start k6: ${result.error.message}`);
  }

  // Preserve failure status: exporting a report must not make a failed test pass.
  exitCode = result.status ?? 1;
  const raw = JSON.parse(readFileSync(rawPath, 'utf8'));
  if (!raw.metrics || typeof raw.metrics !== 'object' || !raw.root_group) {
    throw new Error('Unexpected k6 summary format; no report was written.');
  }

  // Allowlist only metric/check structures; omit setup_data and all other fields.
  const report = {
    metrics: raw.metrics,
    root_group: raw.root_group,
  };
  mkdirSync(dirname(reportPath), { recursive: true });
  writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`Saved aggregate report: ${reportPath}`);
} catch (error) {
  console.error(error instanceof Error ? error.message : 'Could not produce the aggregate report.');
  exitCode = 1;
} finally {
  rmSync(temporaryDirectory, { recursive: true, force: true });
}

process.exitCode = exitCode;
