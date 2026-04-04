import fs from 'fs';
import path from 'path';

const pkg = JSON.parse(fs.readFileSync('./package.json', 'utf8'));
const srcDir = './src';

const deps = {
  ...pkg.dependencies,
  ...pkg.devDependencies,
};

const depNames = Object.keys(deps);
console.log('=== DEPENDENCIES IN package.json ===');
depNames.forEach(d => console.log(`  ${d}`));

console.log('\n=== CHECKING USAGE IN src/ ===');

function getAllFiles(dir) {
    let files = [];
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory() && entry.name !== 'node_modules' && entry.name !== 'test') {
            files = files.concat(getAllFiles(fullPath));
        } else if (entry.isFile() && (entry.name.endsWith('.jsx') || entry.name.endsWith('.js'))) {
            files.push(fullPath);
        }
    }
    return files;
}

const sourceFiles = getAllFiles(srcDir);
const allContent = sourceFiles.map(f => fs.readFileSync(f, 'utf8')).join('\n');

const usedDeps = [];
const unusedDeps = [];

depNames.forEach(dep => {
    // Check various import patterns
    const patterns = [
        `from ['"]${dep}['"]`,
        `require\(['"]${dep}['"]\)`,
        `import\s+.*\s+from\s+['"]${dep}['"]`,
    ];
    
    const isUsed = patterns.some(p => new RegExp(p).test(allContent));
    
    if (isUsed) {
        usedDeps.push(dep);
    } else {
        // Exceptions: things that might be used indirectly
        if (dep === '@vitejs/plugin-react' || dep === 'eslint' || dep === 'globals' || 
            dep === 'jsdom' || dep === 'mysql2' || dep === 'playwright-core') {
            usedDeps.push(dep + ' (dev/build tool)');
        } else {
            unusedDeps.push(dep);
        }
    }
});

if (unusedDeps.length > 0) {
    console.log('\n=== POTENTIALLY UNUSED DEPENDENCIES ===');
    unusedDeps.forEach(d => console.log(`  ${d}`));
} else {
    console.log('\n✓ All dependencies appear to be used!');
}

console.log('\n=== USED DEPENDENCIES ===');
usedDeps.sort().forEach(d => console.log(`  ✓ ${d}`));
