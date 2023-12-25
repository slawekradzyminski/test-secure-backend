export const getAllUsersMocks = {

    mockUsers: () => {
        cy.intercept('GET', '**/users', { fixture: 'users.json' })
    }

}