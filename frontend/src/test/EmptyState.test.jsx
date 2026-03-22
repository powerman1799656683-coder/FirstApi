import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import EmptyState from '../components/EmptyState';
import { AlertCircle } from 'lucide-react';

describe('EmptyState', () => {
    it('应渲染提示文本', () => {
        render(<EmptyState message="暂无数据" />);
        expect(screen.getByText('暂无数据')).toBeInTheDocument();
    });

    it('应有 data-testid', () => {
        render(<EmptyState message="暂无数据" />);
        expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });

    it('有 colSpan 时应渲染为 tr > td 结构', () => {
        const { container } = render(
            <table><tbody><EmptyState colSpan={5} message="暂无数据" /></tbody></table>
        );
        const td = container.querySelector('td');
        expect(td).toBeInTheDocument();
        expect(td.getAttribute('colspan')).toBe('5');
    });

    it('无 colSpan 时应渲染为 div', () => {
        render(<EmptyState message="暂无数据" />);
        const testidEl = screen.getByTestId('empty-state');
        expect(testidEl.tagName).toBe('DIV');
    });

    it('可使用自定义图标', () => {
        const { container } = render(<EmptyState icon={AlertCircle} message="暂无数据" />);
        const svg = container.querySelector('svg');
        expect(svg).toBeInTheDocument();
    });
});
