import { Roles } from "../domain/Roles";
import { User } from "../domain/User"
import { faker } from '@faker-js/faker';

export const getRandomUser = (): User => {
    return {
        firstName: faker.person.firstName(),
        lastName: faker.person.lastName(),
        email: faker.internet.email(),
        password: faker.internet.password(),
        username: faker.internet.userName(),
        roles: [Roles.ROLE_ADMIN, Roles.ROLE_CLIENT]
    }
}