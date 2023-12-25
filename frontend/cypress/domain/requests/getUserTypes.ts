import { Roles } from "../Roles";

export interface GetUserResponse {
    firstName: string,
    lastName: string,
    username: string,
    id: number,
    roles: Roles[],
    email: string
}