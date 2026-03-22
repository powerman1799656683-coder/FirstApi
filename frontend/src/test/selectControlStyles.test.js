import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const css = fs.readFileSync(path.resolve(process.cwd(), 'src/index.css'), 'utf8');

function getCssBlock(selector) {
    const match = css.match(new RegExp(`${selector}\\s*\\{([\\s\\S]*?)\\}`, 'm'));
    return match?.[1] ?? '';
}

describe('custom select in shared controls', () => {
    it('keeps compact control style on trigger when using select-control class', () => {
        const trigger = getCssBlock('\\.custom-select-trigger\\.select-control');

        expect(trigger).toContain('height: 38px');
        expect(trigger).toContain('padding: 0 14px');
        expect(trigger).toContain('transform: none');
    });

    it('keeps compact control sizing in controls rows', () => {
        const controlsTrigger = getCssBlock('\\.controls-group \\.custom-select-trigger');

        expect(controlsTrigger).toContain('height: 38px');
        expect(controlsTrigger).toContain('padding: 0 14px');
    });
});
