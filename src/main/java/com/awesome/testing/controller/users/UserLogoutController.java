package com.awesome.testing.controller.users;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/users")
public class UserLogoutController {

    @Operation(summary = "Logouts customer by expiring the HttpOnly cookie")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        String cookie = "token=null; Max-Age=0; Path=/; HttpOnly; SameSite=None; Secure";
        response.addHeader("Set-Cookie", cookie);
        return ResponseEntity.ok().build();
    }
}
