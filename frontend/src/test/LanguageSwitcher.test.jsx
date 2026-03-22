import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import LanguageSwitcher from '../components/LanguageSwitcher';

const mockI18n = {
    language: 'zh',
    changeLanguage: vi.fn(),
};

vi.mock('react-i18next', () => ({
    useTranslation: () => ({ i18n: mockI18n }),
}));

describe('LanguageSwitcher', () => {
    beforeEach(() => {
        mockI18n.changeLanguage.mockReset();
        mockI18n.language = 'zh';
    });

    it('shows EN when detected language is en-US', () => {
        mockI18n.language = 'en-US';

        render(<LanguageSwitcher />);

        expect(screen.getByRole('button')).toHaveTextContent('EN');
    });

    it('shows ZH when detected language is zh-CN', () => {
        mockI18n.language = 'zh-CN';

        render(<LanguageSwitcher />);

        expect(screen.getByRole('button')).toHaveTextContent('ZH');
    });

    it('changes language after selecting from dropdown', () => {
        render(<LanguageSwitcher />);

        fireEvent.click(screen.getByRole('button'));
        fireEvent.click(screen.getByText('英文'));

        expect(mockI18n.changeLanguage).toHaveBeenCalledWith('en');
    });

    it('supports keyboard dismissal with Escape', () => {
        render(<LanguageSwitcher />);

        fireEvent.click(screen.getByRole('button'));
        expect(screen.getByText('英文')).toBeInTheDocument();

        fireEvent.keyDown(document, { key: 'Escape' });

        expect(screen.queryByText('英文')).not.toBeInTheDocument();
    });

    it('shows a readable Chinese option label', () => {
        render(<LanguageSwitcher />);

        fireEvent.click(screen.getByRole('button'));

        expect(screen.getByText('简体中文')).toBeInTheDocument();
    });
});
