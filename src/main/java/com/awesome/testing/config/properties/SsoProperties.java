package com.awesome.testing.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.sso")
public class SsoProperties {

    private boolean enabled;

    private String authProvider = "keycloak";

    private String issuerUri = "http://localhost:8082/realms/awesome-testing";

    private String jwkSetUri = "http://localhost:8082/realms/awesome-testing/protocol/openid-connect/certs";

    private String audience = "awesome-testing-frontend";

    private String usernameClaim = "preferred_username";

    private String emailClaim = "email";

    private String firstNameClaim = "given_name";

    private String lastNameClaim = "family_name";

}
