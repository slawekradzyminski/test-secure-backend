package com.awesome.testing.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Value;

import javax.validation.constraints.Size;

@Value
public class LoginDTO {

    @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
    @ApiModelProperty(position = 0)
    String username;

    @Size(min = 4, max = 255, message = "Minimum password length: 4 characters")
    @ApiModelProperty(position = 1)
    String password;

}
