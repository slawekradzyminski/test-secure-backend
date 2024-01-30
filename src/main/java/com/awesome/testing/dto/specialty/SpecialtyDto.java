package com.awesome.testing.dto.specialty;

import com.awesome.testing.entities.doctor.SpecialtyEntity;
import lombok.*;

import java.util.List;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SpecialtyDto {

    int id;
    String name;

    public static SpecialtyDto from(SpecialtyEntity specialtyEntity) {
        return SpecialtyDto.builder()
                .id(specialtyEntity.getId())
                .name(specialtyEntity.getName())
                .build();
    }

    public static List<SpecialtyDto> from(List<SpecialtyEntity> specialtiesEntities) {
        return specialtiesEntities.stream()
                .map(SpecialtyDto::from)
                .toList();
    }
}
