package com.awesome.testing.controller;

import com.awesome.testing.dto.EmailDTO;
import com.awesome.testing.jms.JmsSender;
import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@SuppressWarnings("unused")
@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/email")
@Api(tags = "email")
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    @Autowired
    private final JmsSender jmsSender;

    @Value("${activemq.destination}")
    private String destination;

    @PostMapping(value = "")
    @ApiOperation(value = "${JmsSender.sendEmail}")
    public void sendMessage(@RequestBody @Validated EmailDTO email) {
        jmsSender.asyncSendTo(destination, email);
    }

}
