package com.awesome.testing.service;

import com.awesome.testing.dto.users.*;
import com.awesome.testing.entities.doctor.DoctorTypeEntity;
import com.awesome.testing.entities.user.UserEntity;

import com.awesome.testing.repository.DoctorTypeRepository;
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

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final AuthenticationHandler authenticationHandler;
    private final DoctorTypeRepository doctorTypeRepository;

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

    public UserResponseDto updateDoctorTypes(List<Integer> doctorTypeIds) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userRepository.findByUsername(username);
        List<DoctorTypeEntity> doctorTypes = doctorTypeRepository.findAllById(doctorTypeIds);
        user.setDoctorTypes(doctorTypes);
        userRepository.save(user);
        return UserResponseDto.from(user);
    }
}
