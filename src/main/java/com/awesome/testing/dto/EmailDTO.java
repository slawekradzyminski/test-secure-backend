package com.awesome.testing.dto;

import lombok.Value;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@Value
public class EmailDTO {

    @Email
    String to;

    @NotBlank
    String subject;

    @NotBlank
    String message;

}
