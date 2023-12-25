/// <reference types="cypress" />

import { User } from "../domain/User"
import { getRandomUser } from "../generators/userGenerator"

describe('Home page tests', () => {
    let user: User
    let token: string | undefined

    beforeEach(() => {
        user = getRandomUser()
        cy.register(user)
        cy.login(user.username, user.password)
        cy.visit('http://127.0.0.1:8081')
    })

    afterEach(() => {
        cy.request({
            method: 'DELETE',
            url: `http://localhost:4001/users/${user.username}`,
            failOnStatusCode: false
        })
    })

    it('should display at least one user', () => {
        // then
        cy.get('li').should('have.length.at.least', 1)
    })

    it('should logout', () => {
        // when
        cy.get('#logout').click()

        // then
        cy.url().should('contain', '/login')
    })

    it('should open add more page', () => {
        // given
        const userToDelete = getRandomUser()
        cy.register(userToDelete)
        cy.reload()
        cy.intercept('DELETE', `**/users/${userToDelete.username}`).as('deleteRequest')

        // when
        cy.get('li').contains(`${userToDelete.firstName} ${userToDelete.lastName}`).find('.delete').click()

        // then
        cy.get('li').contains(`${userToDelete.firstName} ${userToDelete.lastName}`).should('not.exist')
        cy.wait('@deleteRequest').then((intercepted) => {
            expect(intercepted.response?.statusCode).to.eq(204)
        })
    })

    it('should not delete user if we cancel to delete via window confirm', () => {
        // given
        const userToDelete = getRandomUser()
        cy.register(userToDelete)
        cy.reload()
        Cypress.on('window:confirm', (confirmationText) => {
            expect(confirmationText).to.eq('Are you sure you wish to delete this item?')
            return false
        })
        cy.intercept('DELETE', `**/users/${userToDelete.username}`).as('deleteRequest')

        // when
        cy.get('li').contains(`${userToDelete.firstName} ${userToDelete.lastName}`).find('.delete').click()

        // then
        cy.get('li').contains(`${userToDelete.firstName} ${userToDelete.lastName}`).should('exist')
        cy.get('@deleteRequest.all').then((intercepted) => {
            expect(intercepted).to.have.length(0);
        });
    })
})
