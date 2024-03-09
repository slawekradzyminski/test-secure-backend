package com.awesome.testing.dto.specialty;

import jakarta.validation.constraints.Size;
import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSpecialtyDto {

    @Size(min = 3, max = 255, message = "Minimum specialty length: 3 characters")
    String name;

}
