import { handleResponse } from "./user.service";
import { Email } from "../types";


export const sendEmail = async (email: Email) => {
    const requestOptions = {
        method: 'POST',
        headers: {'Content-Type': 'application/json' },
        credentials: "include" as RequestCredentials,
        body: JSON.stringify(email)
    };

    const response = await fetch(`${process.env.API_URL}/email`, requestOptions);
    return handleResponse(response);
}