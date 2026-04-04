import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const srcDir = './src';
const testDir = './src/test';

// Get all JS/JSX files
function getAllFiles(dir, ext = ['.jsx', '.js']) {
    let files = [];
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            files = files.concat(getAllFiles(fullPath, ext));
        } else if (ext.includes(path.extname(entry.name))) {
            files.push(fullPath);
        }
    }
    return files;
}

// Get all files
const allFiles = getAllFiles(srcDir);
const testFiles = getAllFiles(testDir);
const sourceFiles = allFiles.filter(f => !testFiles.includes(f));

console.log('=== ALL SOURCE FILES ===');
sourceFiles.sort().forEach(f => {
    const rel = path.relative(srcDir, f);
    console.log(rel);
});

console.log(`\nTotal source files: ${sourceFiles.length}`);
console.log(`Total test files: ${testFiles.length}`);
