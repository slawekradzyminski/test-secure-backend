import { Roles } from "./Roles";

export interface User {
    firstName: string,
    lastName: string,
    username: string,
    password: string,
    roles: Roles[],
    email: string
}