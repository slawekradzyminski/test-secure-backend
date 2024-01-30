package com.awesome.testing.service;

import static com.awesome.testing.factory.SlotEntityFactory.createSlotEntityWithClient;
import static com.awesome.testing.factory.SlotEntityFactory.createSlotEntityWithDoctor;
import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.entities.slot.SlotStatus;
import com.awesome.testing.exception.ApiException;
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
        when(slotRepository.existsByDoctorAndStartTimeBetween(any(), any(), any())).thenReturn(false);

        // when
        List<SlotDto> slots = slotService.createSlots(createSlotRangeDto);

        // then
        assertThat(slots).hasSize(14);
        verify(userRepository, times(1)).findByUsername(doctor.getUsername());
        verify(slotRepository, times(14)).save(any(SlotEntity.class));
    }

    @Test
    public void shouldGetAvailableSlots() {
        // given
        LocalDateTime startTime = LocalDateTime.of(2022, 12, 1, 8, 0);
        LocalDateTime endTime = LocalDateTime.of(2022, 12, 1, 15, 0);
        String doctorUsername = "doctorUsername";
        SlotStatus slotStatus = SlotStatus.AVAILABLE;
        Integer specialtyId = 1;

        UserEntity userEntity = new UserEntity();
        userEntity.setUsername(doctorUsername);
        userEntity.setSpecialties(List.of());

        SlotEntity slot1 = createSlotEntityWithDoctor(doctorUsername);
        SlotEntity slot2 = createSlotEntityWithDoctor(doctorUsername);

        List<SlotEntity> slotEntities = List.of(slot1, slot2);
        when(slotRepository.findByCriteria(startTime, endTime, doctorUsername, slotStatus, specialtyId))
                .thenReturn(slotEntities);

        when(userRepository.findAllWithSpecialtiesByUsername(Set.of(doctorUsername)))
                .thenReturn(List.of(userEntity));

        // when
        List<SlotDto> slots = slotService.getAvailableSlots(startTime, endTime, doctorUsername, slotStatus,
                specialtyId);

        // then
        assertThat(slots).hasSize(2);
        verify(slotRepository, times(1)).findByCriteria(startTime, endTime, doctorUsername, slotStatus, specialtyId);
        verify(userRepository, times(1)).findAllWithSpecialtiesByUsername(Set.of(doctorUsername));
    }

    @Test
    public void shouldGetBookedSlots() {
        // given
        String clientUsername = "clientUsername";
        UserEntity clientEntity = new UserEntity();
        clientEntity.setUsername(clientUsername);

        SlotEntity slot1 = createSlotEntityWithClient(clientEntity);
        SlotEntity slot2 = createSlotEntityWithClient(clientEntity);

        List<SlotEntity> slotEntities = List.of(slot1, slot2);
        when(userRepository.findByUsername(clientUsername)).thenReturn(clientEntity);
        when(slotRepository.findByClientAndStatus(clientEntity, SlotStatus.BOOKED)).thenReturn(slotEntities);

        // when
        List<SlotDto> slots = slotService.getBookedSlots(clientUsername);

        // then
        assertThat(slots).hasSize(2);
        verify(userRepository, times(1)).findByUsername(clientUsername);
        verify(slotRepository, times(1)).findByClientAndStatus(clientEntity, SlotStatus.BOOKED);
    }

    @Test
    public void shouldCancelBooking() {
        // given
        String clientUsername = "clientUsername";
        Integer slotId = 1;
        UserEntity clientEntity = new UserEntity();
        clientEntity.setUsername(clientUsername);

        SlotEntity slot = createSlotEntityWithClient(clientEntity);
        slot.setId(slotId);
        slot.setClient(clientEntity);
        slot.setStatus(SlotStatus.BOOKED);

        when(userRepository.findByUsername(clientUsername)).thenReturn(clientEntity);
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        // when
        slotService.cancelBooking(clientUsername, slotId);

        // then
        verify(userRepository, times(1)).findByUsername(clientUsername);
        verify(slotRepository, times(1)).findById(slotId);
        verify(slotRepository, times(1)).save(slot);
        assertThat(slot.getClient()).isNull();
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    public void shouldThrowExceptionWhenCancelBookingNotOwned() {
        // given
        String clientUsername = "clientUsername";
        String otherUsername = "otherUsername";
        Integer slotId = 1;
        UserEntity clientEntity = new UserEntity();
        clientEntity.setUsername(clientUsername);

        UserEntity otherEntity = new UserEntity();
        otherEntity.setUsername(otherUsername);

        SlotEntity slot = createSlotEntityWithClient(otherEntity);
        slot.setId(slotId);
        slot.setClient(otherEntity);
        slot.setStatus(SlotStatus.BOOKED);

        when(userRepository.findByUsername(clientUsername)).thenReturn(clientEntity);
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        // then
        assertThatThrownBy(() -> slotService.cancelBooking(clientUsername, slotId))
                .isInstanceOf(ApiException.class);
    }
}
