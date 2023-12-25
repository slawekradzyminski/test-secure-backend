import { combineReducers } from '@reduxjs/toolkit';

import usersReducer, { UsersState } from './users.reducer';
import edituserReducer, { EditUserState } from "./edituser.reducer";
import authenticationReducer, { AuthenticationState } from './authentication.reducer';
import qrReducer, { QrState } from './qr.reducer';

export type RootState = {
    authentication: AuthenticationState
    edituser: EditUserState
    users: UsersState,
    qr: QrState
};

const rootReducer = combineReducers({
    authentication: authenticationReducer,
    users: usersReducer,
    edituser: edituserReducer,
    qr: qrReducer
});

export default rootReducer;