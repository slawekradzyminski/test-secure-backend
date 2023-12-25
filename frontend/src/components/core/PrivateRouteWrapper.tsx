import React, { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAppDispatch } from '../../_helpers/store';
import { refresh } from '../../_actions/user.actions';
import { unwrapResult } from '@reduxjs/toolkit';
import { useSelector } from 'react-redux';
import { RootState } from '../../_reducers';

function PrivateRouteWrapper({ children }) {
    const location = useLocation();
    const [auth, setAuth] = useState(null);
    const dispatch = useAppDispatch();
    const loggedIn = useSelector((state: RootState) => state.authentication.loggedIn);

    useEffect(() => {
        if (location.pathname !== '/login') {
            dispatch(refresh({}))
                .then(unwrapResult)
                .then((user) => {
                    if (user) {
                        setAuth(true);
                    } else {
                        setAuth(false);
                    }
                })
                .catch(() => setAuth(false));
        }
    }, []);

    if (auth === null) {
        return null;
    }

    return (
        loggedIn || auth
            ? children
            : <Navigate to="/login" replace state={{ from: location }} />
    );
}

export default PrivateRouteWrapper;