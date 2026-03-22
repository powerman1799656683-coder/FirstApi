import React from 'react';
import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import Select from '../components/Select';

describe('Select class binding', () => {
    it('applies select-control class on trigger instead of container to avoid stacked shells', () => {
        const { container } = render(
            <Select className="select-control" value="all" onChange={() => {}}>
                <option value="all">All</option>
                <option value="enabled">Enabled</option>
            </Select>
        );

        const wrapper = container.querySelector('.custom-select-container');
        const trigger = container.querySelector('.custom-select-trigger');

        expect(wrapper).toBeTruthy();
        expect(trigger).toBeTruthy();
        expect(wrapper.classList.contains('select-control')).toBe(false);
        expect(trigger.classList.contains('select-control')).toBe(true);
    });
});
