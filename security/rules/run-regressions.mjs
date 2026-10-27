import { spawnSync } from 'node:child_process';
const suites = [
  './baseline.rules.test.mjs',
  './readonly.candidate.rules.test.mjs',
  './writepilot.candidate.rules.test.mjs',
  './writepilot.fix01.rules.test.mjs',
  './writepilot.final.rules.test.mjs'
];
let status = 0;
for (const suite of suites) {
  process.stdout.write('\n==== SEC-02A+02B / ' + suite + ' ====\n');
  const p = spawnSync(process.execPath, ['--test', suite], { stdio: 'inherit' });
  if (p.error || p.status !== 0) {
    status = 1;
    process.stderr.write('FAIL/BLOCKED: ' + suite + ' (exit ' + p.status + ')\n');
  }
}
process.exitCode = status;
