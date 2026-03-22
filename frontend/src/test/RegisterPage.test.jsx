import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import RegisterPage from '../pages/Register';

const mockRegister = vi.fn();
const mockNavigate = vi.fn();

vi.mock('../auth/AuthContext', () => ({
    useAuth: () => ({
        register: mockRegister,
    }),
}));

vi.mock('react-router-dom', async () => {
    const actual = await vi.importActual('react-router-dom');
    return {
        ...actual,
        useNavigate: () => mockNavigate,
    };
});

describe('RegisterPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders futuristic register essentials', () => {
        render(
            <MemoryRouter>
                <RegisterPage />
            </MemoryRouter>,
        );

        expect(screen.getByText('Create developer account')).toBeInTheDocument();
        expect(screen.getByLabelText('Username')).toBeInTheDocument();
        expect(screen.getByLabelText('Work Email')).toBeInTheDocument();
        expect(screen.getByLabelText('Password')).toBeInTheDocument();
        expect(screen.getByLabelText('Confirm Password')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Create account' })).toBeInTheDocument();
    });

    it('shows mismatch error and blocks submit', async () => {
        render(
            <MemoryRouter>
                <RegisterPage />
            </MemoryRouter>,
        );

        fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'newuser' } });
        fireEvent.change(screen.getByLabelText('Work Email'), { target: { value: 'new@example.com' } });
        fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Password123!' } });
        fireEvent.change(screen.getByLabelText('Confirm Password'), { target: { value: 'Password456!' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Create account' }).form);

        await waitFor(() => {
            expect(screen.getByText('Passwords do not match.')).toBeInTheDocument();
        });
        expect(mockRegister).not.toHaveBeenCalled();
    });

    it('submits normalized payload and navigates to member area', async () => {
        mockRegister.mockResolvedValue({ role: 'USER' });
        render(
            <MemoryRouter>
                <RegisterPage />
            </MemoryRouter>,
        );

        fireEvent.change(screen.getByLabelText('Username'), { target: { value: '  dev  ' } });
        fireEvent.change(screen.getByLabelText('Work Email'), { target: { value: '  dev@example.com  ' } });
        fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Password123!' } });
        fireEvent.change(screen.getByLabelText('Confirm Password'), { target: { value: 'Password123!' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Create account' }).form);

        await waitFor(() => {
            expect(mockRegister).toHaveBeenCalledWith({
                username: 'dev',
                email: 'dev@example.com',
                password: 'Password123!',
                confirmPassword: 'Password123!',
            });
        });
        expect(mockNavigate).toHaveBeenCalledWith('/my-api-keys', { replace: true });
    });
});
