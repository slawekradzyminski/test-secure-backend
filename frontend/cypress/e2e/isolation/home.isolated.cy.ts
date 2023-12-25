/// <reference types="cypress" />

import { User } from "../../domain/User"
import users from "../../fixtures/users.json"

describe('Home page tests in isolation', () => {
    beforeEach(() => {
        cy.isolatedLogin()
        cy.visit('http://127.0.0.1:8081')
    })

    it('should display at least one user', () => {
        // then
        cy.get('li').should('have.length', users.length)
        cy.get('li').each(($el, i) => {
            expect($el.text()).to.contain(`${users[i].firstName} ${users[i].lastName}`)
        })
        cy.percySnapshot('logged in home page')
    })

    it('should delete user', () => {
        // given
        const userToDelete = users[1]
        cy.intercept('DELETE', `**/users/${userToDelete.username}`, { statusCode: 204 }).as('deleteRequest')

        // when
        cy.get('li').contains(`${userToDelete.firstName} ${userToDelete.lastName}`).find('.delete').click()

        // then
        cy.get('li').contains(`${userToDelete.firstName} ${userToDelete.lastName}`).should('not.exist')
        cy.wait('@deleteRequest')
    })

})
