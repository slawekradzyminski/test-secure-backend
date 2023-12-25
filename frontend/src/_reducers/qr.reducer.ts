import { PayloadAction, createSlice } from '@reduxjs/toolkit';
import { createQr } from '../_actions/qr.actions';

export type QrState = {
    url?: string;
};

const initialState: QrState = {};

const qrSlice = createSlice({
    name: 'qr',
    initialState,
    reducers: {
    },
    extraReducers: (builder) => {
        builder
            .addCase(createQr.fulfilled, (state, action: PayloadAction<string>) => {
                state.url = action.payload;
            })
    }
});

export default qrSlice.reducer;