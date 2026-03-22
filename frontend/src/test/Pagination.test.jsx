import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import Pagination from '../components/Pagination';

describe('Pagination component', () => {

    const defaultProps = {
        currentPage: 1,
        totalPages: 5,
        onPageChange: vi.fn(),
        pageSize: 10,
        onPageSizeChange: vi.fn(),
        total: 50,
    };

    it('应渲染分页信息文本', () => {
        render(<Pagination {...defaultProps} />);
        expect(screen.getByTestId('pagination-info')).toHaveTextContent('1-10 / 50');
    });

    it('总页数 <= 7 时应渲染全部页码', () => {
        render(<Pagination {...defaultProps} totalPages={5} />);
        for (let i = 1; i <= 5; i++) {
            expect(screen.getByTestId(`pagination-page-${i}`)).toBeInTheDocument();
        }
        expect(screen.queryByTestId('pagination-ellipsis')).not.toBeInTheDocument();
    });

    it('总页数 > 7 且当前页在前段时应显示后省略号', () => {
        render(<Pagination {...defaultProps} currentPage={2} totalPages={20} total={200} />);
        expect(screen.getByTestId('pagination-page-1')).toBeInTheDocument();
        expect(screen.getByTestId('pagination-page-20')).toBeInTheDocument();
        const ellipses = screen.getAllByTestId('pagination-ellipsis');
        expect(ellipses.length).toBeGreaterThanOrEqual(1);
    });

    it('总页数 > 7 且当前页在中间时应显示两个省略号', () => {
        render(<Pagination {...defaultProps} currentPage={10} totalPages={20} total={200} />);
        expect(screen.getByTestId('pagination-page-1')).toBeInTheDocument();
        expect(screen.getByTestId('pagination-page-20')).toBeInTheDocument();
        const ellipses = screen.getAllByTestId('pagination-ellipsis');
        expect(ellipses.length).toBe(2);
    });

    it('总页数 > 7 且当前页在末段时应显示前省略号', () => {
        render(<Pagination {...defaultProps} currentPage={19} totalPages={20} total={200} />);
        expect(screen.getByTestId('pagination-page-1')).toBeInTheDocument();
        expect(screen.getByTestId('pagination-page-20')).toBeInTheDocument();
        const ellipses = screen.getAllByTestId('pagination-ellipsis');
        expect(ellipses.length).toBeGreaterThanOrEqual(1);
    });

    it('点击页码应调用 onPageChange', () => {
        const onPageChange = vi.fn();
        render(<Pagination {...defaultProps} onPageChange={onPageChange} />);
        fireEvent.click(screen.getByTestId('pagination-page-3'));
        expect(onPageChange).toHaveBeenCalledWith(3);
    });

    it('第一页时上一页按钮应禁用', () => {
        render(<Pagination {...defaultProps} currentPage={1} />);
        const prevButton = screen.getByLabelText('上一页');
        expect(prevButton).toBeDisabled();
    });

    it('最后一页时下一页按钮应禁用', () => {
        render(<Pagination {...defaultProps} currentPage={5} />);
        const nextButton = screen.getByLabelText('下一页');
        expect(nextButton).toBeDisabled();
    });

    it('total=0 时分页信息应为 0-0 / 0', () => {
        render(<Pagination {...defaultProps} total={0} currentPage={1} totalPages={1} />);
        expect(screen.getByTestId('pagination-info')).toHaveTextContent('0-0 / 0');
    });
});
