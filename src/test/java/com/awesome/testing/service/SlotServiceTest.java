package com.awesome.testing.service;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.awesome.testing.AbstractUnitTest;
import com.awesome.testing.dto.slot.CreateSlotRangeDto;
import com.awesome.testing.dto.slot.SlotDto;
import com.awesome.testing.entities.slot.SlotEntity;
import com.awesome.testing.entities.user.UserEntity;
import com.awesome.testing.repository.SlotRepository;
import com.awesome.testing.repository.UserRepository;

public class SlotServiceTest extends AbstractUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SlotRepository slotRepository;

    @InjectMocks
    private SlotService slotService;

    @Test
    public void shouldCreateSlots() {
        // Given
        UserRegisterDto doctor = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        CreateSlotRangeDto createSlotRangeDto = CreateSlotRangeDto.builder()
                .username(doctor.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2022, 12, 1, 8, 0))
                .endAvailability(LocalDateTime.of(2022, 12, 1, 15, 0))
                .build();
        when(userRepository.findByUsername(doctor.getUsername())).thenReturn(UserEntity.from(doctor));
        when(slotRepository.save(any(SlotEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        // when
        List<SlotDto> slots = slotService.createSlots(createSlotRangeDto);

        // then
        assertThat(slots).hasSize(14);
        verify(userRepository, times(1)).findByUsername(doctor.getUsername());
        verify(slotRepository, times(14)).save(any(SlotEntity.class));
    }

}
