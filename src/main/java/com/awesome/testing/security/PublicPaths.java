package com.awesome.testing.security;

import java.util.List;

public class PublicPaths {

    public static final List<String> PUBLIC_PATHS = List.of(
            "/users/signin",
            "/users/signup",
            "/users/logout",
            "/actuator/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/configuration/**",
            "/webjars/**",
            "/public",
            "/h2-console/**",
            "/index*",
            "/static/**",
            "/_ah/*",
            "/*.js",
            "/*.json",
            "/*.ico",
            "/*.jpg",
            "/*.png",
            "/",
            "/login",
            "/register",
            "/static/index.html"
    );

}
