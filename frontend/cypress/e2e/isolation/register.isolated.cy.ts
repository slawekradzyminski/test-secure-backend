/// <reference types="cypress" />

import Alert from "../../components/Alert"
import { buildRegisterRequestFrom } from "../../domain/requests/registerTypes"
import { getRandomUser } from "../../generators/userGenerator"
import { registerMocks } from "../../mocks/registerMocks"
import { registerPage } from "../../pages/registerPage"

describe('Register page tests in isolation', () => {
    beforeEach(() => {
        cy.visit('http://127.0.0.1:8081/register')
    })

    it('should successfully register', () => {
        // given
        const user = getRandomUser()
        registerMocks.mockSuccessfulRegister(user)

        // when
        cy.get('.btn-primary').should('be.visible')
        cy.percySnapshot('register screen')
        registerPage.attemptRegister(user)

        // then
        cy.get('.MuiAlert-message').should('contain.text', 'Registration successful')
        cy.url().should('contain', '/login')
        cy.get('@registerRequest').its('request.body').should('deep.equal', buildRegisterRequestFrom(user))
    })

})

