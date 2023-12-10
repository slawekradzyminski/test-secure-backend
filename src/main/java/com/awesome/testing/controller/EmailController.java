package com.awesome.testing.controller;

import com.awesome.testing.dto.EmailDTO;
import com.awesome.testing.jms.JmsSender;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/email")
@Tag(name = "email")
@RequiredArgsConstructor
public class EmailController {

    @Autowired
    private final JmsSender jmsSender;

    @Value("${activemq.destination}")
    private String destination;

    @PostMapping(value = "")
    @Operation(summary = "Send email", description = "Send an email message to the specified destination")
    public void sendMessage(@RequestBody @Validated EmailDTO email) {
        jmsSender.asyncSendTo(destination, email);
    }

}