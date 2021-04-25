package com.awesome.testing.dto;

import com.awesome.testing.model.Role;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class LoginResponseDTO {

    @ApiModelProperty(position = 0)
    String username;

    @ApiModelProperty(position = 1)
    List<Role> roles;

    @ApiModelProperty(position = 2)
    String firstName;

    @ApiModelProperty(position = 3)
    String lastName;

    @ApiModelProperty(position = 4)
    String token;

    @ApiModelProperty(position = 5)
    String email;

}
