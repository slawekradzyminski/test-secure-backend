import { HttpMethods } from "./httpMethods";

export const editMocks = {

    mockSuccessfulEdit: (username: string) => {
        cy.intercept(HttpMethods.PUT, `**/users/${username}`, { statusCode: 200 }).as('editRequest')
    }

}