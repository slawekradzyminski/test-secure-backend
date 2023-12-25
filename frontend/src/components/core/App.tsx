import React from 'react';
import { BrowserRouter as Router } from 'react-router-dom';
import RoutesComponent from './RouterComponent';
import { ToastProvider } from '../common/ToastProvider';
import ResponsiveAppBar from '../header/ResponsiveAppBar';
import Footer from './Footer';

function App() {
    return (
        <ToastProvider>
            <Router>
                <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
                    <ResponsiveAppBar />
                    <div className="col-md-8 offset-md-2">
                        <RoutesComponent />
                    </div>
                    <Footer />
                </div>
            </Router>
        </ToastProvider>
    );
}

export { App };