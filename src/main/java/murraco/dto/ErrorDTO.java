package murraco.dto;

import lombok.Value;

import java.time.Instant;

@Value
public class ErrorDTO {

    Instant timestamp;
    int status;
    String error;
    String message;
    String path;

}
