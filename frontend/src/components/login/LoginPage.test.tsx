import React from 'react';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { store } from '../../_helpers/store';
import { LoginPage } from './LoginPage';
import { MemoryRouter } from 'react-router-dom';
import fetchMock from 'jest-fetch-mock';
import * as reactRedux from 'react-redux';

jest.mock('react-redux', () => ({
  ...jest.requireActual('react-redux'),
  useDispatch: jest.fn(),
}));

describe('LoginPage', () => {

    let mockDispatch: jest.Mock;

    beforeEach(() => {
        mockDispatch = jest.fn();
        (reactRedux.useDispatch as jest.Mock).mockReturnValue(mockDispatch);

        fetchMock.mockIf(req => req.url.startsWith('/users/logout'), async req => {
            return {
                status: 200,
                body: 'ok'
            };
        });
    });

    afterEach(() => {
        fetchMock.resetMocks();
        jest.clearAllMocks();
    });

    test('renders LoginForm', () => {
        render(
            <Provider store={store}>
                <MemoryRouter>
                    <LoginPage />
                </MemoryRouter>
            </Provider>
        );
        expect(screen.getByText('Sign In')).toBeInTheDocument();
    });

    test('dispatches logout action on mount', () => {
        render(
            <Provider store={store}>
                <MemoryRouter>
                    <LoginPage />
                </MemoryRouter>
            </Provider>
        );

        expect(mockDispatch).toHaveBeenCalled();
    });
});