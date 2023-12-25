
export type User = {
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    roles: Roles[];
    id?: number;
    deleting?: boolean;
    loading?: boolean;
};

export enum Roles {
    ROLE_ADMIN = 'ROLE_ADMIN',
    ROLE_CLIENT = 'ROLE_CLIENT'
}

export type Email = {
    to: string,
    subject: string,
    message: string
}