package com.awesome.testing.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ReactController {
    @RequestMapping({"/login", "/register", "/{path:[^\\.]*}"})
    public ResponseEntity<Resource> index() {
        Resource resource = new ClassPathResource("static/index.html");
        return ResponseEntity.ok(resource);
    }
}
