import React, { useContext, useEffect, useState } from 'react';
import { fetchDoctorTypes, updateDoctorTypes, createDoctorType } from '../../_services/doctorTypes.service';
import { useSelector } from 'react-redux';
import { RootState } from '../../_reducers';
import { userService } from '../../_services/user.service';
import { ToastContext } from '../../context/ToastContext';
import SpecialtyList from './SpecialtyList';
import AddSpecialty from './AddSpecialty';
import { Container } from '@mui/material';

const DoctorTypesComponent = () => {
    const [selectedSpecialties, setSelectedSpecialties] = useState({});
    const [specialties, setSpecialties] = useState([]);
    const [newDoctorType, setNewDoctorType] = useState('');
    const setToast = useContext(ToastContext);
    const username = useSelector((state: RootState) => state.authentication.user.username);

    useEffect(() => {
        const fetchData = async () => {
            const data = await fetchDoctorTypes();
            setSpecialties(data);

            const userDetails = await userService.get(username);
            const userSpecialties = userDetails.doctorTypes
                .reduce((acc, curr) => ({ ...acc, [curr.doctorType]: true }), {});
            setSelectedSpecialties(userSpecialties);
        };

        fetchData();
    }, [username]);

    const handleChange = (event) => {
        setSelectedSpecialties({ ...selectedSpecialties, [event.target.name]: event.target.checked });
    };

    const handleSubmit = async () => {
        const selectedIds = Object.keys(selectedSpecialties)
            .filter(key => selectedSpecialties[key])
            .map(key => specialties.find(specialty => specialty.doctorType === key).id);
        try {
            await updateDoctorTypes({ doctorTypeIds: selectedIds });
            setToast({ open: true, message: 'Doctor types updated successfully!', type: 'success' });
        } catch (error) {
            setToast({ open: true, message: 'Failed to update doctor types!', type: 'error' });
        }
    };

    const handleCreate = async () => {
        try {
            await createDoctorType({ doctorType: newDoctorType });
            setNewDoctorType('');
            const updatedSpecialties = await fetchDoctorTypes();
            setSpecialties(updatedSpecialties);
            setToast({ open: true, message: 'Doctor type created successfully!', type: 'success' });
        } catch (error) {
            setToast({ open: true, message: 'Failed to create doctor type!', type: 'error' });
        }
    };

    return (
        <Container>
            <SpecialtyList
                specialties={specialties}
                selectedSpecialties={selectedSpecialties}
                handleChange={handleChange}
                handleSubmit={handleSubmit}
            />
            <AddSpecialty
                newDoctorType={newDoctorType}
                handleCreate={handleCreate}
                setNewDoctorType={setNewDoctorType}
            />
        </Container>
    );
};

export default DoctorTypesComponent;
