import { User } from "../domain/User";
import { HttpMethods } from "./httpMethods";

const endpoint = '**/users/signup'

export const registerMocks = {

    mockSuccessfulRegister: (user: User) => {
        cy.intercept(HttpMethods.POST, endpoint, {
            statusCode: 201,
            body: {
                token: 'fakeJwTtoken'
            }
        }).as('registerRequest')
    }

}