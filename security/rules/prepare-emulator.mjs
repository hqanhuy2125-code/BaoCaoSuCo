import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

// Firebase CLI restricts rules paths to the directory of firebase.json.
// Stage an EXACT local copy of the tracked root rules under this test project.
// Do not alter the root file, test assertions, or any production Firebase project.
const root = new URL('../../firestore.rules', import.meta.url);
const outputDir = new URL('./.emulator-runtime/', import.meta.url);
const copy = new URL('./.emulator-runtime/firestore.rules', import.meta.url);
const source = readFileSync(root);
const text = source.toString('utf8');
if (!text.includes('match /users/{userId}') ||
    !text.includes('match /{document=**}') ||
    !text.includes('allow read, write: if false')) {
  throw new Error('Unexpected root Firestore Rules; refuse to prepare emulator');
}
mkdirSync(outputDir, { recursive: true });
writeFileSync(copy, source);
if (!readFileSync(copy).equals(source)) {
  throw new Error('Emulator Rules copy differs byte-for-byte from the root');
}
const sha256 = createHash('sha256').update(source).digest('hex');
console.log('SEC-02A: copied tracked root firestore.rules unchanged for local emulator');
console.log('SEC-02A: original / staged rules SHA256 = ' + sha256);
