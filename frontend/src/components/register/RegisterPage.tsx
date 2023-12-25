import React, { useContext } from 'react';
import { useNavigate } from 'react-router-dom';

import { register } from '../../_actions/user.actions';
import { ToastContext } from '../../context/ToastContext';
import { useAppDispatch } from '../../_helpers/store';
import RegisterForm from './RegisterForm';

function RegisterPage() {
    const setToast = useContext(ToastContext);
    const navigate = useNavigate()
    const dispatch = useAppDispatch();

    function handleSubmit(user: any) {
        dispatch(register({ user, setToast, navigate }))
    }

    return (
            <RegisterForm onSubmit={handleSubmit} />
    );
}

export { RegisterPage };