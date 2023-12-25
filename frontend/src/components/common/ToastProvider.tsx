import React, { useState } from 'react';
import { ToastContext } from '../../context/ToastContext';
import { Snackbar, Alert } from '@mui/material';

export const ToastProvider = ({ children }) => {
    const [toast, setToast] = useState(null);

    return (
        <ToastContext.Provider value={setToast}>
            {children}
            {toast && (
                <Snackbar open
                    autoHideDuration={3000}
                    onClose={() => setToast(null)}
                    anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}>
                    <Alert severity={toast.type} onClose={() => setToast(null)}>
                        {toast.message}
                    </Alert>
                </Snackbar>
            )}
        </ToastContext.Provider>
    );
};