// 修复点: 无（纯新增测试）
// 测试覆盖点: Toast 组件 - 类型渲染/自动关闭/手动关闭/持续时间 + 边界（duration=0, 各type颜色）
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import Toast from '../components/Toast';

describe('Toast component', () => {

    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('应渲染消息文本', () => {
        render(<Toast message="操作成功" type="success" onClose={() => {}} />);
        expect(screen.getByText('操作成功')).toBeInTheDocument();
    });

    it('默认3秒后应自动关闭', () => {
        const onClose = vi.fn();
        render(<Toast message="test" type="info" onClose={onClose} />);

        expect(onClose).not.toHaveBeenCalled();

        act(() => {
            vi.advanceTimersByTime(3000);
        });

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('自定义 duration 应在指定时间后关闭', () => {
        const onClose = vi.fn();
        render(<Toast message="test" type="info" onClose={onClose} duration={5000} />);

        act(() => {
            vi.advanceTimersByTime(3000);
        });
        expect(onClose).not.toHaveBeenCalled();

        act(() => {
            vi.advanceTimersByTime(2000);
        });
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('duration=0 不应自动关闭', () => {
        const onClose = vi.fn();
        render(<Toast message="persistent" type="error" onClose={onClose} duration={0} />);

        act(() => {
            vi.advanceTimersByTime(10000);
        });
        expect(onClose).not.toHaveBeenCalled();
    });

    it('点击关闭按钮应调用 onClose', () => {
        const onClose = vi.fn();
        render(<Toast message="closable" type="success" onClose={onClose} />);

        const closeButton = screen.getByRole('button');
        fireEvent.click(closeButton);
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('success 类型应渲染绿色样式', () => {
        const { container } = render(
            <Toast message="success" type="success" onClose={() => {}} />
        );
        const wrapper = container.firstChild;
        expect(wrapper.style.border).toContain('success');
    });

    it('error 类型应渲染红色样式', () => {
        const { container } = render(
            <Toast message="error" type="error" onClose={() => {}} />
        );
        const wrapper = container.firstChild;
        expect(wrapper.style.border).toContain('error');
    });

    it('info 类型应为默认样式', () => {
        const { container } = render(
            <Toast message="info" type="info" onClose={() => {}} />
        );
        const wrapper = container.firstChild;
        expect(wrapper.style.border).toContain('info');
    });

    it('卸载时应清除定时器（不触发额外的 onClose）', () => {
        const onClose = vi.fn();
        const { unmount } = render(
            <Toast message="unmount" type="info" onClose={onClose} duration={3000} />
        );

        unmount();

        act(() => {
            vi.advanceTimersByTime(5000);
        });
        expect(onClose).not.toHaveBeenCalled();
    });

    it('应具有 role="alert" 属性', () => {
        render(<Toast message="alert test" type="info" onClose={() => {}} />);
        expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    it('应具有 aria-live="assertive" 属性', () => {
        render(<Toast message="live test" type="success" onClose={() => {}} />);
        const alert = screen.getByRole('alert');
        expect(alert).toHaveAttribute('aria-live', 'assertive');
    });
});
