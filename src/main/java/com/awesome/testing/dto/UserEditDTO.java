package com.awesome.testing.dto;

import com.awesome.testing.model.Role;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Value
@Builder
public class UserEditDTO {

    @NotEmpty
    @Email
    @ApiModelProperty(position = 0)
    String email;

    @NotEmpty(message = "Please pick at least one role")
    @ApiModelProperty(position = 1)
    List<Role> roles;

    @Size(min = 4, max = 255, message = "Minimum firstName length: 4 characters")
    @ApiModelProperty(position = 2)
    String firstName;

    @Size(min = 4, max = 255, message = "Minimum lastName length: 4 characters")
    @ApiModelProperty(position = 3)
    String lastName;

}
