package com.awesome.testing.dto;

import java.util.List;

import com.awesome.testing.model.Role;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

@Setter
@Getter
@Builder
public class UserRegisterDTO {

    @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
    @ApiModelProperty(position = 0)
    private String username;

    @NotEmpty
    @Email
    @ApiModelProperty(position = 1)
    private String email;

    @Size(min = 4, max = 255, message = "Minimum password length: 4 characters")
    @ApiModelProperty(position = 2)
    private String password;

    @NotEmpty(message = "Please pick at least one role")
    @ApiModelProperty(position = 3)
    private List<Role> roles;

    @Size(min = 4, max = 255, message = "Minimum firstName length: 4 characters")
    @ApiModelProperty(position = 4)
    private String firstName;

    @Size(min = 4, max = 255, message = "Minimum firstName length: 4 characters")
    @ApiModelProperty(position = 5)
    private String lastName;

}
