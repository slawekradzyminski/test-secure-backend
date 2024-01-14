package com.awesome.testing.endpoints.slot;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.slot.CreateSlotRangeDto;
import com.awesome.testing.dto.slot.SlotDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.entities.slot.SlotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class CreateSlotsControllerTest extends DomainHelper {

    private static final String SLOTS_ENDPOINT = "/slots";

    @Test
    public void shouldReturn400ForPastDates() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        CreateSlotRangeDto createSlotRangeDto = CreateSlotRangeDto.builder()
                .username(user.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2022, 12, 1, 8, 0))
                .endAvailability(LocalDateTime.of(2022, 12, 1, 15, 0))
                .build();

        // when
        ResponseEntity<?> response = executePost(SLOTS_ENDPOINT, createSlotRangeDto, getHeadersWith(token),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void shouldReturn400ForEndBeforeStart() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        CreateSlotRangeDto createSlotRangeDto = CreateSlotRangeDto.builder()
                .username(user.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2033, 12, 1, 15, 0))
                .endAvailability(LocalDateTime.of(2033, 12, 1, 8, 0))
                .build();

        // when
        ResponseEntity<?> response = executePost(SLOTS_ENDPOINT, createSlotRangeDto, getHeadersWith(token),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void shouldReturn400ForTooBigAvailability() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        CreateSlotRangeDto createSlotRangeDto = CreateSlotRangeDto.builder()
                .username(user.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2033, 12, 1, 7, 0))
                .endAvailability(LocalDateTime.of(2033, 12, 1, 16, 0))
                .build();

        // when
        ResponseEntity<?> response = executePost(SLOTS_ENDPOINT, createSlotRangeDto, getHeadersWith(token),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void shouldReturn403IfDoctorWantsToSetSlotsForAnotherUser() {
        // given
        UserRegisterDto userToSetSlots = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        register(userToSetSlots);
        UserRegisterDto doctor = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(doctor);
        CreateSlotRangeDto createSlotRangeDto = CreateSlotRangeDto.builder()
                .username(userToSetSlots.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2033, 12, 1, 8, 0))
                .endAvailability(LocalDateTime.of(2033, 12, 1, 15, 0))
                .build();

        // when
        ResponseEntity<?> response = executePost(SLOTS_ENDPOINT, createSlotRangeDto, getHeadersWith(token),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldReturn400IfAdminWantsToSetSlotsForNonDoctor() {
        // given
        UserRegisterDto userToSetSlots = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        register(userToSetSlots);
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(admin);
        CreateSlotRangeDto createSlotRangeDto = CreateSlotRangeDto.builder()
                .username(userToSetSlots.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2033, 12, 1, 8, 0))
                .endAvailability(LocalDateTime.of(2033, 12, 1, 15, 0))
                .build();

        // when
        ResponseEntity<?> response = executePost(SLOTS_ENDPOINT, createSlotRangeDto, getHeadersWith(token),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void doctorsShouldCreateSlotForThemselves() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        CreateSlotRangeDto createSlotRangeDto = CreateSlotRangeDto.builder()
                .username(user.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2033, 12, 1, 8, 0))
                .endAvailability(LocalDateTime.of(2033, 12, 1, 15, 0))
                .build();

        // when
        ResponseEntity<SlotDto[]> response = executePost(SLOTS_ENDPOINT, createSlotRangeDto,
                getHeadersWith(token), SlotDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertSlots(response.getBody(), user);
    }

    @Test
    public void adminsShouldBeAbleToCreateSlotForDoctors() {
        // given
        UserRegisterDto doctor = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        register(doctor);
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(admin);
        CreateSlotRangeDto createSlotRangeDto = CreateSlotRangeDto.builder()
                .username(doctor.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2033, 12, 1, 8, 0))
                .endAvailability(LocalDateTime.of(2033, 12, 1, 15, 0))
                .build();

        // when
        ResponseEntity<SlotDto[]> response = executePost(SLOTS_ENDPOINT, createSlotRangeDto,
                getHeadersWith(token), SlotDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertSlots(response.getBody(), doctor);
    }

    @Test
    public void shouldReturn400IfOverlappingSlots() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        CreateSlotRangeDto createSlotRangeDto = CreateSlotRangeDto.builder()
                .username(user.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2033, 12, 1, 8, 0))
                .endAvailability(LocalDateTime.of(2033, 12, 1, 15, 0))
                .build();
        executePost(SLOTS_ENDPOINT, createSlotRangeDto, getHeadersWith(token), SlotDto[].class);
        CreateSlotRangeDto overlappingCreateSlotRangeDto = CreateSlotRangeDto.builder()
                .username(user.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2033, 12, 1, 14, 0))
                .endAvailability(LocalDateTime.of(2033, 12, 1, 20, 0))
                .build();

        // when
        ResponseEntity<?> response = executePost(SLOTS_ENDPOINT, overlappingCreateSlotRangeDto,
                getHeadersWith(token), Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void shouldReturn403ForClients() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = registerAndThenLoginSavingToken(user);
        CreateSlotRangeDto createSlotRangeDto = CreateSlotRangeDto.builder()
                .username(user.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2033, 12, 1, 8, 0))
                .endAvailability(LocalDateTime.of(2033, 12, 1, 15, 0))
                .build();

        // when
        ResponseEntity<?> response = executePost(SLOTS_ENDPOINT, createSlotRangeDto, getHeadersWith(token),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldReturn403ForUnauthorized() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        registerAndThenLoginSavingToken(user);
        CreateSlotRangeDto createSlotRangeDto = CreateSlotRangeDto.builder()
                .username(user.getUsername())
                .slotDuration(Duration.ofMinutes(30))
                .startAvailability(LocalDateTime.of(2033, 12, 1, 8, 0))
                .endAvailability(LocalDateTime.of(2033, 12, 1, 15, 0))
                .build();

        // when
        ResponseEntity<?> response = executePost(SLOTS_ENDPOINT, createSlotRangeDto, getJsonOnlyHeaders(),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void assertSlots(SlotDto[] body, UserRegisterDto user) {
        List<SlotDto> slots = Arrays.asList(body);
        assertThat(slots).hasSize(14);

        LocalDateTime startTime = LocalDateTime.of(2033, 12, 1, 8, 0);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

        IntStream.range(0, slots.size()).forEach(i -> {
            SlotDto slot = slots.get(i);
            assertThat(slot.getId()).isPositive();
            assertThat(slot.getDoctorFullName()).isEqualTo(String.format("%s %s", user.getFirstName(), user.getLastName()));
            assertThat(slot.getClientUsername()).isNull();
            assertThat(slot.getStartTime()).isEqualTo(startTime.plusMinutes(30L * i).format(formatter));
            assertThat(slot.getEndTime()).isEqualTo(startTime.plusMinutes(30L * i).plusMinutes(30).format(formatter));
            assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        });
    }

}
