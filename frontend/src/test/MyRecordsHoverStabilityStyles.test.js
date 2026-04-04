import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const css = fs.readFileSync(path.resolve(globalThis.process.cwd(), 'src/index.css'), 'utf8');

function getCssBlock(selector) {
    const match = css.match(new RegExp(`${selector}\\s*\\{([\\s\\S]*?)\\}`, 'm'));
    return match?.[1] ?? '';
}

describe('MyRecords hover stability styles', () => {
    it('disables lift animation for stable chart cards on hover', () => {
        const stableHover = getCssBlock('\\.chart-card--stable:hover');

        expect(stableHover).toContain('transform: none');
    });
});
