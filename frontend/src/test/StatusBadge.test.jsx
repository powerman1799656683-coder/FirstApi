import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import StatusBadge from '../components/StatusBadge';

describe('StatusBadge', () => {
    it('应渲染状态文本', () => {
        render(<StatusBadge status="正常" />);
        expect(screen.getByText('正常')).toBeInTheDocument();
    });

    it('正常状态应应用 badge-success class', () => {
        const { container } = render(<StatusBadge status="正常" />);
        const badge = container.querySelector('.badge');
        expect(badge).toHaveClass('badge-success');
    });

    it('异常状态应应用 badge-error class', () => {
        const { container } = render(<StatusBadge status="异常" />);
        const badge = container.querySelector('.badge');
        expect(badge).toHaveClass('badge-error');
    });

    it('暂停状态应应用 badge-warning class', () => {
        const { container } = render(<StatusBadge status="暂停" />);
        const badge = container.querySelector('.badge');
        expect(badge).toHaveClass('badge-warning');
    });

    it('过期状态应应用 badge-neutral class', () => {
        const { container } = render(<StatusBadge status="过期" />);
        const badge = container.querySelector('.badge');
        expect(badge).toHaveClass('badge-neutral');
    });

    it('未知状态应回退到 badge-neutral', () => {
        const { container } = render(<StatusBadge status="未知状态" />);
        const badge = container.querySelector('.badge');
        expect(badge).toHaveClass('badge-neutral');
    });

    it('可通过 variant 手动指定变体', () => {
        const { container } = render(<StatusBadge status="自定义" variant="info" />);
        const badge = container.querySelector('.badge');
        expect(badge).toHaveClass('badge-info');
    });

    it('可传入额外 className', () => {
        const { container } = render(<StatusBadge status="正常" className="extra" />);
        const badge = container.querySelector('.badge');
        expect(badge).toHaveClass('extra');
    });

    it('English status normal 应识别为 success', () => {
        const { container } = render(<StatusBadge status="normal" />);
        const badge = container.querySelector('.badge');
        expect(badge).toHaveClass('badge-success');
    });

    it('English status paused 应识别为 warning', () => {
        const { container } = render(<StatusBadge status="paused" />);
        const badge = container.querySelector('.badge');
        expect(badge).toHaveClass('badge-warning');
    });
});
