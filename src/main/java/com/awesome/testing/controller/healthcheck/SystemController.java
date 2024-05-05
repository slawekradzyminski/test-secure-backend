package com.awesome.testing.controller.healthcheck;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {

    @GetMapping("/_ah/start")
    public String start() {
        return "Needed by Google Cloud Engine";
    }
}