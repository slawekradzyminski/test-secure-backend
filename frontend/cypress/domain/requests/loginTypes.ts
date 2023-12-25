import { Roles } from "../Roles";
import { User } from "../User";

export interface LoginResponse {
    firstName: string,
    lastName: string,
    username: string,
    token: string,
    roles: Roles[],
    email: string
}

export const getLoginResponseFor = (user: User): LoginResponse => {
    // javascript deconstruction
    const { password, ...data } = user
    // javascript spread operator
    return {
        ...data,
        token: 'fakeJwtToken'
    }
}

export const getLoginResponseForWithFirstName = (user: User, firstName: string): LoginResponse => {
    // javascript deconstruction
    const { password, ...data } = user
    // javascript spread operator
    return {
        ...data,
        token: 'fakeJwtToken',
        firstName: firstName
    }
}