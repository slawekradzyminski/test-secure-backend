import React, { useState } from 'react';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import CssBaseline from '@mui/material/CssBaseline';
import TextField from '@mui/material/TextField';
import Box from '@mui/material/Box';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { Roles, User } from '../../types';
import FormHelperText from '@mui/material/FormHelperText';

interface Props {
    onSubmit: (user: User) => void;
}

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const RegisterForm: React.FC<Props> = ({ onSubmit }) => {
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [firstName, setFirstName] = useState('')
    const [lastName, setLastName] = useState('')
    const [email, setEmail] = useState('')
    const [roles, setRoles] = useState([Roles.ROLE_CLIENT])

    const [usernameError, setUsernameError] = useState(false);
    const [passwordError, setPasswordError] = useState(false);
    const [firstNameError, setFirstNameError] = useState(false);
    const [lastNameError, setLastNameError] = useState(false);
    const [emailError, setEmailError] = useState(false);

    function handleSubmit(e: React.FormEvent) {
        e.preventDefault();

        if (!emailRegex.test(email)) {
            setEmailError(true);
            return;
        }

        if (username.length < 3) {
            setUsernameError(true);
            return;
        }

        if (password.length < 3) {
            setPasswordError(true);
            return;
        }

        if (firstName.length < 3) {
            setFirstNameError(true);
            return;
        }

        if (lastName.length < 3) {
            setLastNameError(true);
            return;
        }

        const user = { firstName, lastName, username, password, roles, email }
        onSubmit(user);
    }

    const defaultTheme = createTheme();

    return (
        <ThemeProvider theme={defaultTheme}>
            <Container component="main" maxWidth="xs">
                <CssBaseline />
                <Box
                    sx={{
                        marginTop: 8,
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                    }}
                >
                    <Avatar sx={{ m: 1, bgcolor: 'secondary.main' }}>
                        <PersonAddIcon />
                    </Avatar>
                    <Typography component="h1" variant="h5">
                        Register
                    </Typography>
                    <Box component="form" onSubmit={handleSubmit} noValidate sx={{ mt: 1 }}>
                        <TextField
                            error={firstNameError}
                            margin="normal"
                            required
                            fullWidth
                            id="firstName"
                            label="First Name"
                            name="firstName"
                            autoComplete="firstName"
                            autoFocus
                            value={firstName}
                            onChange={e => {
                                setFirstName(e.target.value);
                                setFirstNameError(e.target.value.length < 3);
                            }}
                        />
                        {firstNameError && <FormHelperText error>First Name must be at least 3 characters long</FormHelperText>}
                        <TextField
                            error={lastNameError}
                            margin="normal"
                            required
                            fullWidth
                            id="lastName"
                            label="Last Name"
                            name="lastName"
                            autoComplete="lastName"
                            value={lastName}
                            onChange={e => {
                                setLastName(e.target.value);
                                setLastNameError(e.target.value.length < 3);
                            }}
                        />
                        {lastNameError && <FormHelperText error>Last Name must be at least 3 characters long</FormHelperText>}
                        <TextField
                            error={usernameError}
                            margin="normal"
                            required
                            fullWidth
                            id="username"
                            label="Username"
                            name="username"
                            autoComplete="username"
                            value={username}
                            onChange={e => {
                                setUsername(e.target.value);
                                setUsernameError(e.target.value.length < 3);
                            }}
                        />
                        {usernameError && <FormHelperText error>Username must be at least 3 characters long</FormHelperText>}
                        <TextField
                            error={passwordError}
                            margin="normal"
                            required
                            fullWidth
                            name="password"
                            label="Password"
                            type="password"
                            id="password"
                            autoComplete="current-password"
                            value={password}
                            onChange={e => {
                                setPassword(e.target.value);
                                setPasswordError(e.target.value.length < 3);
                            }}
                        />
                        {passwordError && <FormHelperText error>Password must be at least 3 characters long</FormHelperText>}
                        <TextField
                            error={emailError}
                            margin="normal"
                            required
                            fullWidth
                            name="email"
                            label="Email"
                            type="email"
                            id="email"
                            autoComplete="email"
                            value={email}
                            onChange={e => {
                                setEmail(e.target.value);
                                setEmailError(!emailRegex.test(e.target.value));
                            }}
                        />
                        {emailError && <FormHelperText error>Email is not valid</FormHelperText>}
                        <Button
                            type="submit"
                            fullWidth
                            variant="contained"
                            sx={{ mt: 3, mb: 2, minWidth: '400px' }}
                            disabled={firstNameError || lastNameError || usernameError || passwordError || emailError}
                        >
                            Register
                        </Button>
                    </Box>
                </Box>
            </Container>
        </ThemeProvider>
    );
}

export default RegisterForm;
