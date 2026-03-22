import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import LoadingSpinner from '../components/LoadingSpinner';

describe('LoadingSpinner', () => {
    it('应渲染 loading-spinner testid', () => {
        render(<LoadingSpinner />);
        expect(screen.getByTestId('loading-spinner')).toBeInTheDocument();
    });

    it('应渲染加载文本', () => {
        render(<LoadingSpinner message="加载中..." />);
        expect(screen.getByText('加载中...')).toBeInTheDocument();
    });

    it('应包含 spin class', () => {
        const { container } = render(<LoadingSpinner />);
        const spinning = container.querySelector('.spin');
        expect(spinning).toBeInTheDocument();
    });

    it('有 colSpan 时应渲染为 tr > td 结构', () => {
        const { container } = render(
            <table><tbody><LoadingSpinner colSpan={3} /></tbody></table>
        );
        const td = container.querySelector('td');
        expect(td).toBeInTheDocument();
        expect(td.getAttribute('colspan')).toBe('3');
    });

    it('无消息时不应渲染文本', () => {
        const { container } = render(<LoadingSpinner />);
        const spans = container.querySelectorAll('span');
        const textSpans = Array.from(spans).filter(s => s.textContent.trim().length > 0);
        expect(textSpans.length).toBe(0);
    });
});
