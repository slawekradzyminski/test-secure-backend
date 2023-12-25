import { User } from "../domain/User"
import { getLoginResponseFor } from "../domain/requests/loginTypes"
import { HttpMethods } from "./httpMethods"

const endpoint = '**/users/signin'

export const loginMocks = {

    mockSuccessfulLogin: (user: User) => {
        cy.intercept(HttpMethods.POST, endpoint, {
            statusCode: 200,
            body: getLoginResponseFor(user)
        }).as('loginRequest')
    },

    mockWrongCredentials: (errorMessage: string) => {
        cy.intercept(HttpMethods.POST, endpoint, {
            statusCode: 422,
            body: {
                message: errorMessage
            }
        })
    }

}