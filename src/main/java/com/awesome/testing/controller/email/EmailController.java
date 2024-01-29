package com.awesome.testing.controller.email;

import com.awesome.testing.controller.utils.authorization.OperationWithSecurity;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForAllRoles;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.jms.JmsSender;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/email")
@Tag(name = "email")
@RequiredArgsConstructor
public class EmailController {

    private final JmsSender jmsSender;

    @Value("${activemq.destination}")
    private String destination;

    @PostMapping
    @PreAuthorizeForAllRoles
    @OperationWithSecurity(summary = "Send email to the specified destination")
    public void sendMessage(@RequestBody @Validated EmailDto email) {
        jmsSender.asyncSendTo(destination, email);
    }

}