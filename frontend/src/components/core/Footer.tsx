import React from 'react';
import { Container, Typography } from '@mui/material';

const Footer = () => {
    return (
        <footer>
            <Container maxWidth="sm">
                <br />
                <br />
                <Typography variant="body2" color="text.secondary" align="center">
                    {'Copyright © '}
                    Sławomir Radzymiński Consulting {new Date().getFullYear()}
                </Typography>
                <br />
                <br />
            </Container>
        </footer>
    );
}

export default Footer;
