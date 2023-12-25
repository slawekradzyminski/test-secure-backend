import { createAsyncThunk } from '@reduxjs/toolkit';
import { CreateQrDto, generateQrCode } from '../_services/qr.service';

export const createQr = createAsyncThunk<any,
    { createQrDto: CreateQrDto },
    { rejectValue: string }>(
        'qr/create',
        async ({ createQrDto }, { dispatch, rejectWithValue }) => {
            try {
                const blob = await generateQrCode(createQrDto);
                const url = URL.createObjectURL(blob);
                return url;
            } catch (error) {
                return rejectWithValue(error.toString());
            }
        }
    );