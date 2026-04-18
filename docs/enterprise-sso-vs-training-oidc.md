# Enterprise SSO vs This Training OIDC Bridge

## Short Version

Enterprise SSO is an organization-wide identity architecture. It connects employees, contractors, devices, policies, directories, audit trails, and many applications through a trusted identity provider such as Microsoft Entra ID, Google Workspace, Okta, Ping, Auth0, or an enterprise Keycloak deployment.

Our proposed solution is smaller on purpose. It is a local OIDC login path for a training environment. It demonstrates the mechanics of OAuth/OIDC and gives testers realistic flows to automate, while the application still issues and uses its own JWT and refresh tokens.

In other words:

```text
Enterprise SSO: company identity is the source of truth.
Training OIDC bridge: external login proves identity once, then our app issues its normal tokens.
```

## What Enterprise SSO Means

Enterprise SSO usually includes more than a login button.

It commonly covers:

- Central identity provider for the whole organization.
- User lifecycle management: joiner, mover, leaver.
- Directory integration such as Entra ID / Active Directory / Google Directory.
- MFA and conditional access policies.
- Device posture and location-based rules.
- Group, role, and entitlement synchronization.
- Audit logs and compliance reporting.
- Consent management and app registration governance.
- SCIM provisioning and deprovisioning.
- Account recovery and helpdesk workflows.
- Session policies and sometimes single logout.
- Multiple applications trusting the same identity provider.

The application usually does not own the full identity lifecycle. It trusts identity, groups, claims, and policy decisions from the organization's IdP.

Examples:

- A company employee logs in to Microsoft Entra ID and gets access to Jira, GitHub Enterprise, Slack, internal dashboards, and HR tools.
- A Google Workspace account controls who can access a SaaS admin panel.
- Okta provisions users into applications and removes them when employment ends.

## What Our Solution Is

Our solution is an OIDC bridge for a controlled local stack.

It adds:

- A local identity provider, preferably Keycloak.
- A browser-based Authorization Code + PKCE login flow.
- A backend token exchange endpoint.
- OIDC token validation.
- Local user provisioning.
- Normal app JWT and refresh token issuance after successful SSO login.
- Playwright scenarios for redirect-based login and token handling.

It deliberately does not make the IdP the full source of truth for the app.

The app still owns:

- Local `app_user` records.
- App roles such as `ROLE_CLIENT` and `ROLE_ADMIN`.
- App JWT format.
- App refresh token rotation.
- Existing bearer-token authorization.
- Existing password login path.

## Flow Comparison

### Enterprise SSO Flow

```text
User opens enterprise app
  -> app redirects to corporate IdP
  -> corporate IdP applies policy: MFA, device, risk, location, group membership
  -> app receives trusted identity claims
  -> app grants access based on enterprise identity and entitlements
```

The enterprise IdP often remains important throughout the session because organization policy may change access decisions.

### Training OIDC Bridge Flow

```text
User opens training app
  -> app redirects to local Keycloak
  -> Keycloak authenticates test user
  -> frontend sends OIDC token to backend
  -> backend validates token once
  -> backend creates or finds local app user
  -> backend returns app JWT + app refresh token
  -> app continues exactly like normal password login
```

After exchange, backend APIs use the app JWT, not the raw Keycloak token.

## Why We Use An App Token After SSO

The current app already has a stable authentication model:

- `POST /api/v1/users/signin` returns an app JWT and refresh token.
- The frontend stores those tokens.
- API clients send `Authorization: Bearer <app-jwt>`.
- The backend validates the app JWT through its own security filter.
- Refresh and logout are already implemented.

The OIDC bridge preserves that model. SSO becomes another way to obtain the same app session.

This gives us:

- Minimal disruption to existing tests.
- Clear teaching separation between "external login" and "internal authorization".
- No need to rework every endpoint to understand provider-specific tokens.
- A simple way to keep password login and SSO login side by side.

## Comparison Table

| Area | Enterprise SSO | Training OIDC Bridge |
|---|---|---|
| Main purpose | Organization-wide identity and access management | Local, repeatable OAuth/OIDC training scenario |
| Identity provider | Microsoft Entra ID, Google Workspace, Okta, Auth0, Ping, enterprise Keycloak | Local Keycloak container |
| Source of truth | Corporate directory / IdP | App database after first token exchange |
| User lifecycle | Managed centrally by organization | Created locally for the training app |
| MFA / conditional access | Core feature | Usually not included in first version |
| Groups and roles | Synced from IdP or directory | Initially mapped to default `ROLE_CLIENT` |
| App token | May use IdP access token directly or app session | Always use app-issued JWT after exchange |
| Refresh behavior | Often provider/session policy driven | Existing app refresh token flow |
| Logout | May include single logout or central session revocation | Initially local app logout only |
| Operational ownership | Identity/security/platform teams | Training stack maintainers |
| Setup cost | Medium to high | Low to medium |
| Best testing focus | Enterprise policy, provisioning, claims, compliance | OAuth redirects, token validation, app provisioning, Playwright flows |

## What Students Can Learn From Our Version

The local bridge is intentionally scoped, but it still teaches the parts testers and developers usually struggle with:

- OAuth is redirect-based, not just a JSON login endpoint.
- PKCE protects browser-based authorization code flows.
- Redirect URIs must match exactly.
- Tokens have issuer, audience, expiry, and signature requirements.
- A valid token from the wrong issuer must fail.
- A valid token for the wrong client must fail.
- OIDC identity is not the same as local app authorization.
- First login often creates or links a local app account.
- Account linking needs explicit conflict rules.
- Browser automation needs to handle cross-origin IdP pages.
- API tests can validate token exchange without always driving the full UI.

This is enough to answer common training questions without requiring every student to have a Microsoft, Google, or Okta tenant.

## What It Does Not Teach By Default

The first implementation should not pretend to be enterprise SSO.

It will not fully cover:

- SCIM provisioning.
- HR-driven deprovisioning.
- Conditional access.
- Device compliance.
- Real MFA rollout.
- Enterprise consent workflows.
- Complex group-to-role mapping.
- Multi-tenant SaaS identity.
- SAML.
- Single logout.
- Admin approval and app registration governance.
- Production incident response for identity outages.

Those can be discussed as extensions after the core OIDC flow is understood.

## Common Misconceptions

### "If We Add Google Login, We Have Enterprise SSO"

Not necessarily.

Google Login can be a social login, a workspace login, or part of enterprise SSO depending on tenant policy, domain restrictions, group mapping, audit requirements, and lifecycle management.

A login button alone is not enterprise SSO.

### "OAuth Is Authentication"

OAuth 2.0 is primarily an authorization framework. OIDC adds an identity layer on top of OAuth 2.0.

For login, we should speak about OIDC, ID tokens, user identity claims, issuer, audience, and nonce/state handling.

### "The Backend Should Accept Any Valid Provider Token"

No.

The backend should accept only tokens from the configured issuer and intended audience. In our design, raw OIDC tokens are accepted only by the exchange endpoint. Protected business APIs continue to require the app JWT.

### "SSO Means No Local Users"

Usually false.

Many applications still keep local user records for app-specific settings, preferences, audit references, carts, orders, ownership, prompts, and domain data. SSO proves who the person is; the application still needs its own domain model.

### "Logout Is Simple"

Local logout and enterprise single logout are different.

Local logout clears the app session or app refresh token. Single logout may also terminate the identity provider session and affect other applications. That is more complicated and should not be part of the first training implementation.

## Recommended Wording For Trainings

Use this phrasing:

> This environment demonstrates an OIDC-based SSO-style login. A local Keycloak server acts as the identity provider. After successful external login, the backend exchanges the OIDC identity for the app's normal JWT and refresh token. This is not a full enterprise SSO platform, but it exercises the important OAuth/OIDC testing surfaces.

Avoid this phrasing:

> We implemented Microsoft/Google enterprise SSO.

That would imply directory integration, enterprise policy, lifecycle management, and production identity governance that this training stack does not provide.

## When To Use Which Test Strategy

Use password login tests when:

- The test is about normal app behavior.
- Auth is only setup noise.
- You need fast, deterministic fixtures.
- You are testing carts, products, orders, prompts, email, or traffic logs.

Use SSO API exchange tests when:

- You are testing issuer, audience, expiry, signature, or claim handling.
- You are testing account provisioning.
- You are testing email conflict behavior.
- You want fast feedback without browser redirects.

Use SSO UI tests when:

- You are testing the login button and callback route.
- You are testing Playwright behavior across the IdP redirect.
- You are teaching how to automate OAuth/OIDC browser flows.

Keep SSO UI tests smaller and separately tagged because they are slower and more fragile than regular API authentication fixtures.

## Future Enterprise Extensions

If the training stack later needs a more enterprise-like module, add features in this order:

1. Role mapping from Keycloak groups to app roles.
2. Explicit account linking flow.
3. Domain restrictions such as only `@example.com`.
4. MFA policy in Keycloak.
5. Admin-only user provisioning controls.
6. Provider logout redirect.
7. Multiple providers.
8. SCIM-like provisioning simulation.
9. SAML comparison module.

Each step should come with dedicated tests and docs. Do not hide these behaviors inside the first SSO implementation.
