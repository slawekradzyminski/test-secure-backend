import React, { useCallback, useContext, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { login, logout } from '../../_actions/user.actions';
import { useAppDispatch } from '../../_helpers/store';
import LoginForm from './LoginForm';
import { ToastContext } from '../../context/ToastContext';

const DEFAULT_PATH = "/";

function LoginPage() {
    const navigate = useNavigate();
    const dispatch = useAppDispatch();
    const location = useLocation();
    const setToast = useContext(ToastContext);

    useEffect(() => {
        dispatch(logout());
    }, [dispatch]);

    const handleSubmit = useCallback(({ username, password }) => {
        const { from } = location.state || { from: { pathname: DEFAULT_PATH } };
        dispatch(login({ username, password, from, navigate, setToast }));
    }, [dispatch, location, navigate]);

    return (
        <LoginForm onSubmit={handleSubmit} />
    );
}

export { LoginPage };