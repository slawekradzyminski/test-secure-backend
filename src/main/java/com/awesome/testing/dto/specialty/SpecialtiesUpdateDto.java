package com.awesome.testing.dto.specialty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SpecialtiesUpdateDto {
    private List<Integer> specialtyIds;
}
