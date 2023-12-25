import { createSlice } from '@reduxjs/toolkit';
import { User } from '../types';
import { update } from '../_actions/user.actions';

export type EditUserState = {
    loading?: boolean;
    userToEdit?: User;
    error?: any;
}

let initialState: EditUserState = {};

const editUserSlice = createSlice({
    name: 'editUser',
    initialState,
    reducers: {
    },
    extraReducers: (builder) => {
        builder
            .addCase(update.pending, (state) => {
                state.loading = true;
            })
            .addCase(update.fulfilled, (state, action) => {
                state.loading = false;
                state.userToEdit = action.payload;
            })
            .addCase(update.rejected, (state, action) => {
                state.loading = false;
                state.error = action.payload;
            })
    }
});

export default editUserSlice.reducer;