import { handleResponse } from "./user.service";

export const fetchDoctorTypes = async () => {
    const requestOptions = {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' },
        credentials: "include" as RequestCredentials,
    };

    const response = await fetch(`${process.env.API_URL}/doctortypes`, requestOptions);
    return handleResponse(response);
};

export const updateDoctorTypes = async (doctorTypeUpdateDto: { doctorTypeIds: number[] }) => {
    const requestOptions = {
        method: 'PUT',
        headers: { 
            'Content-Type': 'application/json',
        },
        credentials: "include" as RequestCredentials,
        body: JSON.stringify(doctorTypeUpdateDto)
    };

    const response = await fetch(`${process.env.API_URL}/users/doctortypes`, requestOptions);
    return handleResponse(response);
};

export const createDoctorType = async (createDoctorTypeDto: { doctorType: string }) => {
    const requestOptions = {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
        },
        credentials: "include" as RequestCredentials,
        body: JSON.stringify(createDoctorTypeDto)
    };

    const response = await fetch(`${process.env.API_URL}/doctortypes`, requestOptions);
    return handleResponse(response);
};