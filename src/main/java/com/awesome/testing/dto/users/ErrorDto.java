package com.awesome.testing.dto.users;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDto {

    Instant timestamp;
    int status;
    String error;
    String message;
    String path;

}
