import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import Select from './Select';

const DEFAULT_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

function buildPageNumbers(currentPage, totalPages) {
    if (totalPages <= 7) {
        return Array.from({ length: totalPages }, (_, i) => i + 1);
    }

    const pages = [];
    pages.push(1);

    if (currentPage > 4) {
        pages.push('...');
    }

    const start = Math.max(2, currentPage - 2);
    const end = Math.min(totalPages - 1, currentPage + 2);

    for (let i = start; i <= end; i++) {
        pages.push(i);
    }

    if (currentPage < totalPages - 3) {
        pages.push('...');
    }

    if (totalPages > 1) {
        pages.push(totalPages);
    }

    return pages;
}

export default function Pagination({
    currentPage,
    totalPages,
    onPageChange,
    pageSize,
    onPageSizeChange,
    total,
    pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
}) {
    const safeTotal = total || 0;
    const safeTotalPages = Math.max(1, totalPages);
    const startRow = safeTotal === 0 ? 0 : (currentPage - 1) * pageSize + 1;
    const endRow = safeTotal === 0 ? 0 : Math.min(currentPage * pageSize, safeTotal);

    const pageNumbers = buildPageNumbers(currentPage, safeTotalPages);

    return (
        <div
            data-testid="pagination"
            style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: '12px 20px',
                borderTop: '1px solid var(--border-color)',
            }}
        >
            <div style={{ color: 'var(--text-muted)', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span data-testid="pagination-info">
                    {startRow}-{endRow} / {safeTotal}
                </span>
                <Select
                    className="select-control"
                    style={{ padding: '3px 6px', fontSize: '12px', minWidth: '55px' }}
                    value={pageSize}
                    onChange={(e) => onPageSizeChange(Number(e.target.value))}
                >
                    {pageSizeOptions.map((s) => (
                        <option key={s} value={s}>{s}</option>
                    ))}
                </Select>
            </div>
            <div style={{ display: 'flex', gap: '4px' }}>
                <button
                    className="select-control"
                    style={{ padding: '3px' }}
                    disabled={currentPage === 1}
                    onClick={() => onPageChange(currentPage - 1)}
                    aria-label="上一页"
                >
                    <ChevronLeft size={14} />
                </button>
                {pageNumbers.map((item, idx) =>
                    item === '...' ? (
                        <span
                            key={`ellipsis-${idx}`}
                            data-testid="pagination-ellipsis"
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                minWidth: '28px',
                                color: 'var(--text-muted)',
                                fontSize: '12px',
                                userSelect: 'none',
                            }}
                        >
                            ...
                        </span>
                    ) : (
                        <button
                            key={item}
                            className="select-control"
                            data-testid={`pagination-page-${item}`}
                            style={
                                item === currentPage
                                    ? {
                                          background: 'rgba(59, 130, 246, 0.1)',
                                          color: 'var(--primary-tech)',
                                          border: '1px solid rgba(59, 130, 246, 0.3)',
                                      }
                                    : {}
                            }
                            onClick={() => onPageChange(item)}
                        >
                            {item}
                        </button>
                    )
                )}
                <button
                    className="select-control"
                    style={{ padding: '3px' }}
                    disabled={currentPage === safeTotalPages}
                    onClick={() => onPageChange(currentPage + 1)}
                    aria-label="下一页"
                >
                    <ChevronRight size={14} />
                </button>
            </div>
        </div>
    );
}
