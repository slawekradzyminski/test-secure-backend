package com.awesome.testing.controller.utils.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Operation(security = {@SecurityRequirement(name = "Authorization")})
public @interface OperationWithSecurity {
    String summary();
}