import { createSlice } from '@reduxjs/toolkit';
import { User } from '../types';
import { login, logout, refresh } from '../_actions/user.actions';

export type AuthenticationState = {
  loggedIn?: boolean;
  loading?: boolean;
  user?: User;
  error? : string
};

const initialState: AuthenticationState = {}

const authenticationSlice = createSlice({
  name: 'authentication',
  initialState,
  extraReducers: (builder) => {
    builder
      .addCase(login.pending, (state) => {
        state.loading = true;
      })
      .addCase(login.fulfilled, (state, action) => {
        state.loading = false;
        state.user = action.payload;
        state.loggedIn = true;
      })
      .addCase(login.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      })
      .addCase(refresh.fulfilled, (state, action) => {
        state.user = action.payload;
        state.loggedIn = true;
      })
      .addCase(logout.pending, (state) => {
        state.loading = true;
      })
      .addCase(logout.fulfilled, (state) => {
        state.loading = false;
        state.user = null;
        state.loggedIn = false;
      })
      .addCase(logout.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      })
  },
  reducers: {
  },
});


export default authenticationSlice.reducer;