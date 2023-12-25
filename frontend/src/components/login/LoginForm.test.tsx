import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginForm from './LoginForm';

describe('LoginForm', () => {
    let mockSubmit: jest.Mock;

    beforeEach(() => {
        mockSubmit = jest.fn();
    });

    test('validates inputs and allows form submission', async () => {
        // given
        render(<LoginForm onSubmit={mockSubmit} />);
        const usernameInput = screen.getByLabelText(/username/i);
        const passwordInput = screen.getByLabelText(/password/i);
        const submitButton = screen.getByText('Sign In');

        // when
        await userEvent.type(usernameInput, 'testuser');
        await userEvent.type(passwordInput, 'testpass');
        await userEvent.click(submitButton);

        // then
        expect(mockSubmit).toHaveBeenCalledWith({ username: 'testuser', password: 'testpass' });
    });

    test('shows error when username is less than 3 characters', async () => {
        // given
        render(<LoginForm onSubmit={mockSubmit} />);
        const usernameInput = screen.getByLabelText(/username/i);

        // when
        await userEvent.type(usernameInput, 'te');

        // then
        expect(screen.getByText('Username must be at least 3 characters long')).toBeInTheDocument();
        expect(mockSubmit).not.toHaveBeenCalled();
    });

    test('shows error when password is less than 3 characters', async () => {
        // given
        render(<LoginForm onSubmit={mockSubmit} />);
        const passwordInput = screen.getByLabelText(/password/i);

        // when
        await userEvent.type(passwordInput, 'te');

        // then
        expect(screen.getByText('Password must be at least 3 characters long')).toBeInTheDocument();
        expect(mockSubmit).not.toHaveBeenCalled();
    });

    test('navigates to /register when Sign Up link is clicked', () => {
        // given
        render(<LoginForm onSubmit={mockSubmit} />);

        // then
        expect(screen.getByText("Don't have an account? Sign Up")).toHaveAttribute('href', '/register')
    });
});

