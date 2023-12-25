import './commands'
import '@percy/cypress'
import './isolatedTestCommands'

beforeEach('setup', () => {
    cy.log('This will be logged before each test')
})