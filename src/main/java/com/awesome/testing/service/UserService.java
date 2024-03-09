package com.awesome.testing.service;

import com.awesome.testing.dto.users.*;
import com.awesome.testing.entities.user.UserEntity;

import com.awesome.testing.repository.SpecialtiesRepository;
import jakarta.servlet.http.HttpServletRequest;

import com.awesome.testing.exception.ApiException;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.security.AuthenticationHandler;
import com.awesome.testing.security.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final AuthenticationHandler authenticationHandler;
    private final SpecialtiesRepository specialtiesRepository;

    public LoginResponseDto signIn(LoginDto loginDetails) {
        String token = authenticationHandler.authenticateUserAndGetToken(loginDetails);
        UserEntity userEntity = search(loginDetails.getUsername());
        return LoginResponseDto.from(userEntity, token);
    }

    public void signUp(UserRegisterDto userRegisterDTO) {
        if (userRepository.existsByUsername(userRegisterDTO.getUsername())) {
            throw new ApiException("Username is already in use", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String encryptedPassword = passwordEncoder.encode(userRegisterDTO.getPassword());
        userRepository.save(UserEntity.from(userRegisterDTO, encryptedPassword));
    }

    public void delete(String username) {
        search(username);
        userRepository.deleteByUsername(username);
    }

    public UserEntity search(String username) {
        return Optional.ofNullable(userRepository.findByUsername(username))
                .orElseThrow(() -> new ApiException("The user doesn't exist", HttpStatus.NOT_FOUND));
    }

    public List<UserResponseDto> getAll() {
        return userRepository.findAll()
                .stream()
                .map(UserResponseDto::from)
                .toList();
    }

    public UserEntity whoAmI(HttpServletRequest req) {
        String token = jwtTokenUtil.extractTokenFromRequest(req);
        return userRepository.findByUsername(jwtTokenUtil.getUsername(token));
    }

    public String refreshToken(String username) {
        return jwtTokenUtil.createToken(username, userRepository.findByUsername(username).getRoles());
    }

    public void edit(String username, UserEditDto userEditBody) {
        UserEntity userEntity = search(username);
        userEntity.setFirstName(userEditBody.getFirstName());
        userEntity.setLastName(userEditBody.getLastName());
        userEntity.setEmail(userEditBody.getEmail());
        userEntity.setRoles(userEditBody.getRoles());
        userRepository.save(userEntity);
    }

    public UserResponseDto updateSpecialties(List<Integer> specialtyIds) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userRepository.findByUsername(username);
        user.setSpecialties(specialtiesRepository.findAllById(specialtyIds));
        userRepository.save(user);
        return UserResponseDto.from(user);
    }

    public void updateUserProfilePicture(String username, MultipartFile file) {
        validateImage(file);
        UserEntity user = search(username);
        try {
            byte[] bytes = file.getBytes();
            user.setProfilePicture(bytes);
            userRepository.save(user);
        } catch (IOException e) {
            throw new RuntimeException("Error saving file", e);
        }
    }

    public byte[] getProfilePicture(String username) {
        UserEntity user = search(username);
        return user.getProfilePicture();
    }

    private void validateImage(MultipartFile file) {
        if (!file.getContentType().matches("image/jpeg|image/png|image/gif")) {
            throw new ApiException("Invalid file type", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > 1024 * 1024) { // 1MB
            throw new ApiException("File size exceeds limit", HttpStatus.BAD_REQUEST);
        }
    }
}
