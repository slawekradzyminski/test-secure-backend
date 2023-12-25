import { getRandomUser } from "../generators/userGenerator"
import { getAllUsersMocks } from "../mocks/getAllUsersMocks"
import { refreshMocks } from "../mocks/refreshUserMocks"

Cypress.Commands.add('isolatedLogin', () => {
    const user = getRandomUser()
    getAllUsersMocks.mockUsers()
    refreshMocks.mockSuccessfulRefresh(user)
})