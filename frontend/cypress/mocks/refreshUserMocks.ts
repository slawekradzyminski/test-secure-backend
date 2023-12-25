import { User } from "../domain/User"
import { getLoginResponseFor } from "../domain/requests/loginTypes"
import { HttpMethods } from "./httpMethods"

const endpoint = '**/users/refresh'

export const refreshMocks = {

    mockSuccessfulRefresh: (user: User) => {
        cy.intercept(HttpMethods.GET, endpoint, {
            statusCode: 200,
            body: getLoginResponseFor(user)
        }).as('refreshRequest')
    },

}