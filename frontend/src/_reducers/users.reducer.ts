import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { User } from '../types';
import { getAll, _delete } from '../_actions/user.actions';

export type UsersState = {
  loading?: boolean;
  items?: User[];
  error?: any;
};

const initialState: UsersState = {};

const usersSlice = createSlice({
  name: 'users',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(getAll.pending, (state) => {
        state.loading = true;
      })
      .addCase(getAll.fulfilled, (state, action: PayloadAction<User[]>) => {
        state.items = action.payload;
        state.loading = false;
      })
      .addCase(getAll.rejected, (state, action: PayloadAction<string>) => {
        state.error = action.payload;
        state.loading = false;
      })
      .addCase(_delete.pending, (state, action: PayloadAction<string>) => {
        state.items = state.items?.map((user) =>
          user.username === action.payload
            ? { ...user, deleting: true }
            : user
        );
      })
      .addCase(_delete.fulfilled, (state, action: PayloadAction<string>) => {
        state.items = state.items?.filter((user) => user.username !== action.payload);
      })
      .addCase(_delete.rejected, (state, action: PayloadAction<{ username: string; error: any }>) => {
        state.items = state.items?.map((user) => {
          if (user.username === action.payload.username) {
            const { deleting, ...userCopy } = user;
            return { ...userCopy, deleteError: action.payload.error };
          }

          return user;
        });
      });
  },
});

export default usersSlice.reducer;