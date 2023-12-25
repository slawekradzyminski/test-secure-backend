import { User } from "../domain/User";

declare global {
    namespace Cypress {
        interface Chainable {
            // commands.ts
            login(username: string, password: string): void;
            register(user: User): void;

            // isolatedTestCommands.ts
            isolatedLogin(): void;
        }
    }
}