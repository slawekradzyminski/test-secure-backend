/// <reference types="cypress" />

import Alert from "../../components/Alert"
import { User } from "../../domain/User"
import { getRandomUser } from "../../generators/userGenerator"
import { getAllUsersMocks } from "../../mocks/getAllUsersMocks"
import { loginMocks } from "../../mocks/loginMocks"
import { refreshMocks } from "../../mocks/refreshUserMocks"
import LoginPage from "../../pages/LoginPage"

describe('Login page tests in isolation', () => {
    beforeEach(() => {
        cy.visit('http://127.0.0.1:8081')
    })

    it('should successfully login', () => {
        // given
        const user = getRandomUser()
        loginMocks.mockSuccessfulLogin(user)
        getAllUsersMocks.mockUsers()

        // when
        cy.get('.btn-primary').should('be.visible')
        cy.percySnapshot('logging screen')
        LoginPage.attemptLogin(user.username, user.password)

        // then
        cy.get('h1').should('contain.text', user.firstName)
        verifyCorrectRequestWasBuild(user)
    })

    it('should fail to login', () => {
        // given
        const errorMessage = 'Invalid username/password supplied'
        loginMocks.mockWrongCredentials(errorMessage)

        // when
        LoginPage.attemptLogin('wrong', 'wrong')

        // then
        Alert.getAlertError().should('contain.text', errorMessage)
        cy.url().should('contain', '/login')
    })

    it('should trigger frontend validation', () => {
        // when
        LoginPage.clickLogin()

        // then
        cy.get('.invalid-feedback').should('have.length', 2)
        Alert.getAlertError().should('be.visible')
        cy.percySnapshot('frontent validation')
    })

    it('should remove alert after chaning page', () => {
         // given
         const user = getRandomUser()
         loginMocks.mockWrongCredentials("error")
         LoginPage.attemptLogin(user.username, user.password)
         Alert.getAlertError().should('be.visible')
         loginMocks.mockSuccessfulLogin(user)
         getAllUsersMocks.mockUsers()
         refreshMocks.mockSuccessfulRefresh(user)
 
         // when
         LoginPage.attemptLogin(user.username, user.password)
 
         // then
         cy.get('h1').should('contain.text', user.firstName)
         Alert.getAlertError().should('not.exist')
    })

})

const verifyCorrectRequestWasBuild = (user: User) => {
    cy.get('@loginRequest').its('request.body').should('deep.equal', {
        username: user.username,
        password: user.password
    })
}
