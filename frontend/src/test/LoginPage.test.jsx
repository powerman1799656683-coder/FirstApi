import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from '../pages/Login';

const mockLogin = vi.fn();
const mockNavigate = vi.fn();
let mockLocationState = null;

const translations = {
    'login.subtitle': 'login-subtitle',
    'login.username': 'username-label',
    'login.password': 'password-label',
    'login.username_placeholder': 'username-placeholder',
    'login.password_placeholder': 'password-placeholder',
    'login.login_button': 'login-button',
    'login.verifying': 'verifying',
    'login.no_account': 'no-account',
    'login.register_now': 'register-now',
    'login.error_required': 'missing-credentials',
    'login.error_failed': 'login-failed',
};

vi.mock('../auth/AuthContext', () => ({
    useAuth: () => ({
        login: mockLogin,
        publicConfig: {
            siteName: 'BrandName',
        },
    }),
}));

vi.mock('../components/LanguageSwitcher', () => ({
    default: () => <div data-testid="language-switcher">language-switcher</div>,
}));

vi.mock('react-i18next', () => ({
    useTranslation: () => ({
        t: (key) => translations[key] ?? key,
        i18n: {
            resolvedLanguage: 'zh',
            language: 'zh',
            changeLanguage: vi.fn(),
        },
    }),
}));

vi.mock('react-router-dom', async () => {
    const actual = await vi.importActual('react-router-dom');
    return {
        ...actual,
        useNavigate: () => mockNavigate,
        useLocation: () => ({ state: mockLocationState }),
    };
});

describe('LoginPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        window.localStorage.clear();
        mockLocationState = null;
    });

    it('renders the current login essentials and keeps the site name inside the badge', () => {
        render(
            <MemoryRouter>
                <LoginPage />
            </MemoryRouter>,
        );

        expect(screen.getByText('BrandName')).toBeInTheDocument();
        expect(screen.getByText('login-subtitle')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('username-placeholder')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('password-placeholder')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'login-button' })).toBeInTheDocument();
        expect(screen.getByTestId('language-switcher')).toBeInTheDocument();

        const logoBadge = screen.getByTestId('login-logo-badge');

        expect(logoBadge).toHaveTextContent('BrandName');
        expect(logoBadge).toHaveStyle({ width: 'fit-content', minWidth: '56px', overflow: 'hidden' });
    });

    it('submits the trimmed username and remembers it by default', async () => {
        mockLogin.mockResolvedValue({ role: 'ADMIN' });

        render(
            <MemoryRouter>
                <LoginPage />
            </MemoryRouter>,
        );

        fireEvent.change(screen.getByPlaceholderText('username-placeholder'), {
            target: { value: '  admin  ' },
        });
        fireEvent.change(screen.getByPlaceholderText('password-placeholder'), {
            target: { value: 'AdminPass123!' },
        });
        fireEvent.click(screen.getByRole('button', { name: 'login-button' }));

        await waitFor(() => {
            expect(mockLogin).toHaveBeenCalledWith({
                username: 'admin',
                password: 'AdminPass123!',
            });
        });

        expect(mockNavigate).toHaveBeenCalledWith('/', { replace: true });
        expect(window.localStorage.getItem('firstapi.remember.username')).toBe('admin');
    });

    it('shows a validation error when username or password is missing', async () => {
        render(
            <MemoryRouter>
                <LoginPage />
            </MemoryRouter>,
        );

        fireEvent.click(screen.getByRole('button', { name: 'login-button' }));

        expect(await screen.findByText('missing-credentials')).toBeInTheDocument();
        expect(mockLogin).not.toHaveBeenCalled();
    });
});
