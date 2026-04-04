import fs from 'fs';
import path from 'path';

const srcDir = './src';

function getAllSourceFiles(dir) {
    let files = [];
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory() && entry.name !== 'test') {
            files = files.concat(getAllSourceFiles(fullPath));
        } else if ((entry.isFile() && (entry.name.endsWith('.jsx') || entry.name.endsWith('.js')))) {
            files.push(fullPath);
        }
    }
    return files;
}

function extractImports(content) {
    const imports = [];
    const importRegex = /import\s+(?:(?:\{[^}]*\}|\*\s+as\s+\w+|\w+(?:\s*,\s*\{[^}]*\})?)\s+from\s+)?['"]([^'"]+)['"]/g;
    let match;
    while ((match = importRegex.exec(content)) !== null) {
        const importPath = match[1];
        if (!importPath.startsWith('react') && !importPath.startsWith('lucide-react') && !importPath.startsWith('i18next')) {
            imports.push(importPath);
        }
    }
    return imports;
}

function extractExports(content) {
    const exports = [];
    const exportRegex = /export\s+(?:default\s+)?(?:function|const|class)\s+(\w+)/g;
    let match;
    while ((match = exportRegex.exec(content)) !== null) {
        exports.push(match[1]);
    }
    return exports;
}

const allFiles = getAllSourceFiles(srcDir);
const fileContents = {};
const importMap = {};

allFiles.forEach(file => {
    const rel = path.relative(srcDir, file);
    const content = fs.readFileSync(file, 'utf8');
    fileContents[rel] = content;
    importMap[rel] = extractImports(content);
});

console.log('=== FILES IN APP ROUTING (Used) ===');
const appContent = fs.readFileSync('./src/App.jsx', 'utf8');
const appImports = appContent.match(/import\s+(\w+)\s+from\s+['"]\.\/pages\/\w+['"]/g) || [];
console.log('App.jsx imports these pages:');
appImports.forEach(imp => console.log('  ' + imp));

console.log('\n=== PAGES DIRECTORY FILES ===');
const pageFiles = allFiles.filter(f => f.includes('/pages/'));
pageFiles.sort().forEach(f => {
    const rel = path.relative(srcDir, f);
    const imported = appImports.some(imp => imp.includes(path.basename(f).replace('.jsx', '')));
    console.log(`${imported ? '[USED]' : '[UNUSED]'} ${rel}`);
});

console.log('\n=== COMPONENT FILES ===');
const componentFiles = allFiles.filter(f => f.includes('/components/'));
componentFiles.sort().forEach(f => {
    const rel = path.relative(srcDir, f);
    console.log(rel);
});
