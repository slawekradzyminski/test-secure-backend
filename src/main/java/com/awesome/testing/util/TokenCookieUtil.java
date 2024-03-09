package com.awesome.testing.util;

import lombok.Builder;

@Builder
public class TokenCookieUtil {

    public static String buildTokenCookie(String token, long tokenValidityInSeconds) {
        return "token=" + token +
                "; Max-Age=" + tokenValidityInSeconds +
                "; Path=/" +
                "; HttpOnly" +
                "; SameSite=None" +
                "; Secure";
    }

}
