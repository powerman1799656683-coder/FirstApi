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

function analyzeFile(filePath) {
    const content = fs.readFileSync(filePath, 'utf8');
    const lines = content.split('\n');
    
    // Find imports
    const importLines = [];
    const importMatches = content.matchAll(/import\s+(?:{([^}]*)}|(\w+))\s+from\s+['"]([^'"]+)['"]/g);
    
    const issues = [];
    
    for (const match of importMatches) {
        const destructured = match[1];
        const defaultImport = match[2];
        const source = match[3];
        
        // Skip external libs
        if (source.startsWith('react') || source.startsWith('lucide') || source.startsWith('i18')) {
            continue;
        }
        
        if (destructured) {
            const imports = destructured.split(',').map(s => s.trim().split(/\s+as\s+/).pop());
            for (const imp of imports) {
                if (!imp) continue;
                
                // Check if this import is used in the file (excluding the import statement itself)
                const importPattern = new RegExp(`\b${imp}\b(?!\s*[,}]|\s+as\s+)`, 'g');
                const contentWithoutImports = lines.slice(30).join('\n');
                const matches = contentWithoutImports.match(importPattern) || [];
                
                if (matches.length === 0) {
                    issues.push({
                        type: 'unused-import',
                        name: imp,
                        source: source,
                    });
                }
            }
        }
    }
    
    return issues;
}

const allFiles = getAllSourceFiles(srcDir);
const results = {};

allFiles.forEach(file => {
    const rel = path.relative(srcDir, file);
    const issues = analyzeFile(file);
    if (issues.length > 0) {
        results[rel] = issues;
    }
});

if (Object.keys(results).length > 0) {
    console.log('=== POTENTIAL UNUSED IMPORTS ===\n');
    Object.entries(results).forEach(([file, issues]) => {
        console.log(`${file}:`);
        issues.forEach(issue => {
            console.log(`  - Import "${issue.name}" from "${issue.source}" may be unused`);
        });
        console.log();
    });
} else {
    console.log('✓ No obvious unused imports found');
}
