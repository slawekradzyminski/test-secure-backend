/// <reference types="cypress" />

describe('Login page tests', () => {
    beforeEach(() => {
      cy.visit('http://127.0.0.1:8081')
    })

    it('should successfully login', () => {
        cy.get('[name=username]').type('admin')
        cy.get('[name=password]').type('admin')
        cy.get('.btn-primary').click()

        cy.get('h1').should('contain.text', 'Slawomir')
    })

  })
  