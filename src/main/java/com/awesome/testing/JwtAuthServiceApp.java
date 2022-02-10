package com.awesome.testing;

import java.util.List;

import com.awesome.testing.service.OspsService;
import com.awesome.testing.service.SjpService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.awesome.testing.model.Role;
import com.awesome.testing.model.User;
import com.awesome.testing.service.UserService;
import org.zalando.logbook.HeaderFilter;

import static org.zalando.logbook.HeaderFilter.none;

@SpringBootApplication
@RequiredArgsConstructor
public class JwtAuthServiceApp implements CommandLineRunner {

    private final UserService userService;
    private final OspsService ospsService;
    private final SjpService sjpService;
    private final FileResourcesUtils fileResourcesUtils;

    public static void main(String[] args) {
        SpringApplication.run(JwtAuthServiceApp.class, args);
    }

    @SuppressWarnings("unused")
    @Bean
    public HeaderFilter headerFilter() {
        return none();
    }

    @SuppressWarnings("unused")
    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    @Override
    public void run(String... params) {
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword("admin");
        admin.setEmail("admin@email.com");
        admin.setFirstName("Slawomir");
        admin.setLastName("Radzyminski");
        admin.setRoles(List.of(Role.ROLE_ADMIN, Role.ROLE_CLIENT));
        userService.signUp(admin);

        User client = new User();
        client.setUsername("client");
        client.setPassword("client");
        client.setEmail("client@email.com");
        client.setFirstName("Gosia");
        client.setLastName("Radzyminska");
        client.setRoles(List.of(Role.ROLE_CLIENT));
        userService.signUp(client);

        fileResourcesUtils.getAllLines("osps.txt").parallelStream()
                .forEach(ospsService::addWord);

//        fileResourcesUtils.getAllLines("sjp.txt").parallelStream()
//                .forEach(sjpService::addWord);
    }

}
