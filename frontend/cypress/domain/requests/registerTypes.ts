import { Roles } from "../Roles";
import { User } from "../User";

export interface RegisterRequest {
    firstName: string,
    lastName: string,
    username: string,
    password: string,
    roles: Roles[],
    email: string
}

export const buildRegisterRequestFrom = (user: User): RegisterRequest => {
    return {
        ...user,
        roles: [Roles.ROLE_CLIENT]
    }
}