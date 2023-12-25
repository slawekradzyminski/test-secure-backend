import * as React from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import BugReportIcon from '@mui/icons-material/BugReport';
import { Link } from 'react-router-dom';
import { loggedInPages, pagePaths, loggedOutPages } from './navbarConstants';
import { useSelector } from 'react-redux';
import { RootState } from '../../_reducers';

const DesktopView = () => {
    const loggedIn = useSelector((state: RootState) => state.authentication.loggedIn);

    return (
        <>
            <BugReportIcon sx={{ display: { xs: 'none', md: 'flex' }, mr: 1 }} />
            <Typography
                variant="h6"
                noWrap
                component="a"
                href="#app-bar-with-responsive-menu"
                sx={{
                    mr: 2,
                    display: { xs: 'none', md: 'flex' },
                    fontFamily: 'monospace',
                    fontWeight: 700,
                    letterSpacing: '.3rem',
                    color: 'inherit',
                    textDecoration: 'none',
                }}
            >
                LOGO
            </Typography>
            <Box sx={{ flexGrow: 1, display: { xs: 'none', md: 'flex' } }}>
                {loggedIn ? (
                    loggedInPages.map((page) => (
                        <Button
                            key={page}
                            sx={{ my: 2, color: 'white', display: 'block' }}
                            component={Link}
                            to={pagePaths[page]}
                        >
                            {page}
                        </Button>
                    ))
                ) : (
                    loggedOutPages.map((page) => (
                        <Button
                            key={page}
                            sx={{ my: 2, color: 'white', display: 'block' }}
                            component={Link}
                            to={pagePaths[page]}
                        >
                            {page}
                        </Button>
                    ))
                )}
            </Box>
        </>
    )
};

export default DesktopView;