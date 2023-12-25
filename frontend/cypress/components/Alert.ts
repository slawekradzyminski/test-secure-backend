export default class Alert {

    static getAlertSuccess = () => cy.get('.alert-success')
    static getAlertError = () => cy.get('.alert-danger')

}