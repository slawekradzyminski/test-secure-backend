package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.awesome.testing.dbsetup.h2.InitialUsers.*;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class UsersSetup {

    private final UserService userService;

    public void setupUsers() {
        userService.signUp(getAdmin());
        userService.signUp(getClient());
        userService.signUp(getDoctor());
    }

}
