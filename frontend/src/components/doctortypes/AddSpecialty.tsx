import { Box, Paper, Typography, TextField, Button } from "@mui/material";
import React from "react";

const AddSpecialty = ({ newDoctorType, handleCreate, setNewDoctorType }) => (
    <Box mt={6} p={2} component={Paper} elevation={3} bgcolor="grey.100">
        <Typography variant="h6" align="center" gutterBottom>
            Can't find your specialty? Add it!
        </Typography>
        <Box display="flex" justifyContent="center" mt={2}>
            <TextField value={newDoctorType} onChange={(e) => setNewDoctorType(e.target.value)} label="New Doctor Type" />
            <Button variant="contained" color="primary" onClick={handleCreate} style={{ marginLeft: '10px' }}>
                Add Doctor Type
            </Button>
        </Box>
    </Box>
);

export default AddSpecialty