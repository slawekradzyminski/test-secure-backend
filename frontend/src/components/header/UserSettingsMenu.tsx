import React from 'react';
import Box from '@mui/material/Box';
import Tooltip from '@mui/material/Tooltip';
import IconButton from '@mui/material/IconButton';
import Avatar from '@mui/material/Avatar';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import { settings } from './navbarConstants';
import { useNavigate } from 'react-router-dom';

interface UserSettingsMenuProps {
    anchorElUser: HTMLElement | null;
    handleOpenUserMenu: (event: React.MouseEvent<HTMLElement>) => void;
    handleCloseUserMenu: () => void;
}

const UserSettingsMenu: React.FC<UserSettingsMenuProps> = ({
    anchorElUser,
    handleOpenUserMenu,
    handleCloseUserMenu,
}) => {
    const navigate = useNavigate();

    const handleClick = (setting: string) => {
        if (setting === 'Logout') {
            navigate('/login')
        }
        handleCloseUserMenu();
    };

    return (
        <Box sx={{ flexGrow: 0 }}>
            <Tooltip title="Open settings">
                <IconButton onClick={handleOpenUserMenu} sx={{ p: 0 }}>
                    <Avatar alt="Remy Sharp" src="/static/images/avatar/2.jpg" />
                </IconButton>
            </Tooltip>
            <Menu
                sx={{ mt: '45px' }}
                id="menu-appbar"
                anchorEl={anchorElUser}
                anchorOrigin={{
                    vertical: 'top',
                    horizontal: 'right',
                }}
                keepMounted
                transformOrigin={{
                    vertical: 'top',
                    horizontal: 'right',
                }}
                open={Boolean(anchorElUser)}
                onClose={handleCloseUserMenu}
            >
                {settings.map((setting) => (
                    <MenuItem key={setting} onClick={() => handleClick(setting)}>
                        <Typography textAlign="center">{setting}</Typography>
                    </MenuItem>
                ))}
            </Menu>
        </Box>
    )
};

export default UserSettingsMenu;