import { logout } from "../_actions/user.actions";

export type CreateQrDto = {
    text: string
}

export const generateQrCode = async (createQrDto: CreateQrDto) => {
    const requestOptions = {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: "include" as RequestCredentials,
        body: JSON.stringify(createQrDto)
    };

    const response = await fetch(`${process.env.API_URL}/qr/create`, requestOptions);
    return handleImageResponse(response);
}

export const handleImageResponse = async (response: Response) => {
    if (!response.ok) {
        if (response.status === 403) {
            logout();
            throw new Error('Not authenticated');
        }
        const error = response.statusText;
        return Promise.reject(error);
    }
    return response.blob();
}