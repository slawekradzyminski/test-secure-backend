/// <reference types="cypress" />

import { User } from "../domain/User"
import { getRandomUser } from "../generators/userGenerator"

describe('Edit page tests', () => {
    let user: User
    let token: string | undefined

    beforeEach(() => {
        user = getRandomUser()
        cy.register(user)
        cy.login(user.username, user.password)
        cy.visit('http://127.0.0.1:8081')
        cy.get('li').contains(`${user.firstName} ${user.lastName}`).find('.edit').click()
    })

    afterEach(() => {
        cy.request({
            method: 'DELETE',
            url: `http://localhost:4001/users/${user.username}`,
            failOnStatusCode: false
        })
    })

    it('should successfully edit an user', () => {
        // given
        const newUser = getRandomUser()

        // when
        cy.get('[name=firstName]').clear().type(newUser.firstName)
        cy.get('[name=lastName]').clear().type(newUser.lastName)
        cy.get('[name=email]').clear().type(newUser.email)
        cy.get('.btn-primary').click()

        // then
        cy.get('li').contains(`${user.firstName} ${user.lastName}`).should('not.exist')
        cy.get('li').contains(`${newUser.firstName} ${newUser.lastName}`).should('exist')
        cy.request({
            method: 'GET',
            url: `http://localhost:4001/users/${user.username}`,
        }).then((resp) => {
            expect(resp.body.firstName).to.eq(newUser.firstName)
            expect(resp.body.lastName).to.eq(newUser.lastName)
            expect(resp.body.email).to.eq(newUser.email)
            expect(resp.body.username).to.eq(user.username)
        })
    })

})
