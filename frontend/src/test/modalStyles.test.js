import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const css = fs.readFileSync(path.resolve(process.cwd(), 'src/index.css'), 'utf8');

function getCssBlock(selector) {
    const match = css.match(new RegExp(`${selector}\\s*\\{([\\s\\S]*?)\\}`, 'm'));
    return match?.[1] ?? '';
}

describe('shared modal styles', () => {
    it('pins the modal overlay to the viewport and centers the dialog', () => {
        const overlay = getCssBlock('\\.modal-overlay');

        expect(overlay).toContain('position: fixed');
        expect(overlay).toContain('inset: 0');
        expect(overlay).toContain('display: flex');
        expect(overlay).toContain('justify-content: center');
        expect(overlay).toContain('align-items: center');
        expect(overlay).toContain('z-index: 1000');
    });

    it('keeps shared modal content constrained and scrollable', () => {
        const content = getCssBlock('\\.modal-content');

        expect(content).toContain('width: 100%');
        expect(content).toContain('max-width: 600px');
        expect(content).toContain('max-height: 90vh');
        expect(content).toContain('display: flex');
        expect(content).toContain('flex-direction: column');
        expect(content).toContain('overflow-y: auto');
    });
});
