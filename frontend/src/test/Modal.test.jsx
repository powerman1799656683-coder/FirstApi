// 修复点: 无（纯新增测试）
// 测试覆盖点: Modal 组件 - 渲染/关闭/错误显示/footer/children + 边界（isOpen=false, 无error, 无footer）
import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import Modal from '../components/Modal';

describe('Modal component', () => {

    it('isOpen=false 时不渲染任何内容', () => {
        const { container } = render(
            <Modal isOpen={false} onClose={() => {}} title="Test" />
        );
        expect(container.innerHTML).toBe('');
    });

    it('isOpen=true 时渲染 modal 内容', () => {
        render(
            <Modal isOpen={true} onClose={() => {}} title="测试标题">
                <p>模态框内容</p>
            </Modal>
        );
        expect(screen.getByText('测试标题')).toBeInTheDocument();
        expect(screen.getByText('模态框内容')).toBeInTheDocument();
    });

    it('点击关闭按钮应调用 onClose', () => {
        const onClose = vi.fn();
        render(
            <Modal isOpen={true} onClose={onClose} title="Close Test" />
        );
        const closeButton = document.querySelector('.modal-close');
        fireEvent.click(closeButton);
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('有 error 时应显示错误信息', () => {
        render(
            <Modal isOpen={true} onClose={() => {}} title="Error Test" error="出错了">
                <p>内容</p>
            </Modal>
        );
        expect(screen.getByTestId('modal-error')).toBeInTheDocument();
        expect(screen.getByText('出错了')).toBeInTheDocument();
    });

    it('无 error 时不显示错误区域', () => {
        render(
            <Modal isOpen={true} onClose={() => {}} title="No Error">
                <p>内容</p>
            </Modal>
        );
        expect(screen.queryByTestId('modal-error')).not.toBeInTheDocument();
    });

    it('有 footer 时应渲染 footer 区域', () => {
        render(
            <Modal isOpen={true} onClose={() => {}} title="Footer Test"
                   footer={<button>确定</button>}>
                <p>内容</p>
            </Modal>
        );
        expect(screen.getByText('确定')).toBeInTheDocument();
    });

    it('无 footer 时不渲染 footer 区域', () => {
        const { container } = render(
            <Modal isOpen={true} onClose={() => {}} title="No Footer">
                <p>内容</p>
            </Modal>
        );
        expect(container.querySelector('.modal-footer')).not.toBeInTheDocument();
    });

    it('点击 modal-content 内部不应关闭（stopPropagation）', () => {
        const onClose = vi.fn();
        render(
            <Modal isOpen={true} onClose={onClose} title="Propagation Test">
                <p>内容</p>
            </Modal>
        );
        const content = screen.getByTestId('modal-content');
        fireEvent.click(content);
        expect(onClose).not.toHaveBeenCalled();
    });

    it('data-testid 属性应正确设置', () => {
        render(
            <Modal isOpen={true} onClose={() => {}} title="TestID" />
        );
        expect(screen.getByTestId('modal-overlay')).toBeInTheDocument();
        expect(screen.getByTestId('modal-content')).toBeInTheDocument();
    });

    it('点击遮罩层不应关闭弹窗', () => {
        const onClose = vi.fn();
        render(
            <Modal isOpen={true} onClose={onClose} title="Overlay Close Guard" />
        );
        fireEvent.mouseDown(screen.getByTestId('modal-overlay'));
        fireEvent.click(screen.getByTestId('modal-overlay'));
        expect(onClose).not.toHaveBeenCalled();
    });

    it('应具有 role="dialog" 和 aria-modal="true"', () => {
        render(
            <Modal isOpen={true} onClose={() => {}} title="ARIA Test" />
        );
        const dialog = screen.getByRole('dialog');
        expect(dialog).toBeInTheDocument();
        expect(dialog).toHaveAttribute('aria-modal', 'true');
    });

    it('aria-labelledby 应指向标题元素', () => {
        render(
            <Modal isOpen={true} onClose={() => {}} title="Label Test" />
        );
        const dialog = screen.getByRole('dialog');
        const labelledById = dialog.getAttribute('aria-labelledby');
        expect(labelledById).toBeTruthy();
        const titleElement = document.getElementById(labelledById);
        expect(titleElement).toBeInTheDocument();
        expect(titleElement.textContent).toBe('Label Test');
    });

    it('关闭按钮应有 aria-label', () => {
        render(
            <Modal isOpen={true} onClose={() => {}} title="Close Aria" />
        );
        const closeButton = screen.getByLabelText('关闭');
        expect(closeButton).toBeInTheDocument();
    });

    it('按 Escape 键应调用 onClose', () => {
        const onClose = vi.fn();
        render(
            <Modal isOpen={true} onClose={onClose} title="Escape Test" />
        );
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('打开时应锁定 body 滚动', () => {
        const { unmount } = render(
            <Modal isOpen={true} onClose={() => {}} title="Scroll Lock" />
        );
        expect(document.body.style.overflow).toBe('hidden');
        unmount();
    });
});
