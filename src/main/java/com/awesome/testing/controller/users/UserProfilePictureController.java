package com.awesome.testing.controller.users;

import com.awesome.testing.controller.utils.authorization.OperationWithSecurity;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForAllRoles;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = { "http://localhost:8081", "http://127.0.0.1:8081" }, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/users")
@Tag(name = "users")
@RequiredArgsConstructor
public class UserProfilePictureController {

    private final UserService userService;

    @PreAuthorizeForAllRoles
    @PostMapping(value = "/{username}/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @OperationWithSecurity(summary = "Upload profile picture")
    public void uploadProfilePicture(@PathVariable String username,
            @RequestParam("file") MultipartFile file) {
        userService.updateUserProfilePicture(username, file);
    }

    @PreAuthorizeForAllRoles
    @OperationWithSecurity(summary = "Get profile picture")
    @GetMapping(value = "/{username}/profile-picture", produces = MediaType.MULTIPART_FORM_DATA_VALUE)
    public byte[] getProfilePicture(@PathVariable String username) {
        return userService.getProfilePicture(username);
    }

}
