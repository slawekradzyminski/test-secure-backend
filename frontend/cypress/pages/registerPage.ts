import { User } from "../domain/User";

export const registerPage = {

    attemptRegister: (user: User) => {
        cy.get('[name=username]').type(user.username)
        cy.get('[name=password]').type(user.password)
        cy.get('[name=firstName]').type(user.firstName)
        cy.get('[name=lastName]').type(user.lastName)
        cy.get('[name=email]').type(user.email)
        cy.get('.btn-primary').click()
    }

}