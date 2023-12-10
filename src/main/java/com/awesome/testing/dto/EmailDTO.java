package com.awesome.testing.dto;

import lombok.Value;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Value
public class EmailDTO {

    @Email
    String to;

    @NotBlank
    String subject;

    @NotBlank
    String message;

}
