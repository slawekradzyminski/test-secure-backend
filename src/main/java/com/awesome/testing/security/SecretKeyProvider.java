package com.awesome.testing.security;

import javax.crypto.SecretKey;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * THIS IS NOT A SECURE PRACTICE! For simplicity, we are storing a static key
 * here. Ideally, in a
 * microservices environment, this key would be kept on a config-server.
 */
@Component
@Getter
public class SecretKeyProvider {

    private final SecretKey secretKey;

    public SecretKeyProvider(@Value("${security.jwt.token.secret-key:secret-key}") String secretKey) {
        String encodedKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
        byte[] keyBytes = Decoders.BASE64.decode(encodedKey);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

}
