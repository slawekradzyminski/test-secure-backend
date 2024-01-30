package com.awesome.testing.service;

import com.awesome.testing.dto.slot.CreateSlotRangeDto;
import com.awesome.testing.dto.slot.SlotDto;
import com.awesome.testing.entities.slot.SlotEntity;
import com.awesome.testing.entities.slot.SlotStatus;
import com.awesome.testing.entities.user.UserEntity;
import com.awesome.testing.exception.ApiException;
import com.awesome.testing.repository.SlotRepository;
import com.awesome.testing.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final SlotRepository slotRepository;
    private final UserRepository userRepository;

    public List<SlotDto> createSlots(CreateSlotRangeDto createSlotRangeDto) {
        UserEntity doctor = getUser(createSlotRangeDto.getUsername());

        if (slotRepository.existsByDoctorAndStartTimeBetween(doctor, createSlotRangeDto.getStartAvailability(),
                createSlotRangeDto.getEndAvailability())) {
            throw new ApiException("Slots already exist in the provided time range", HttpStatus.BAD_REQUEST);
        }

        return IntStream.range(0, calculateTheNumberOfSlots(createSlotRangeDto))
                .mapToObj(index -> saveSlotEntity(createSlotRangeDto, index, doctor))
                .map(slotEntity -> SlotDto.from(slotEntity, doctor))
                .toList();
    }

    public List<SlotDto> getBookedSlots(String username) {
        UserEntity client = getUser(username);
        List<SlotEntity> slots = slotRepository.findByClientAndStatus(client, SlotStatus.BOOKED);
        return slots.stream()
                .map(slot -> SlotDto.from(slot, slot.getDoctor()))
                .toList();
    }

    public void bookSlot(String username, Integer slotId) {
        UserEntity client = getUser(username);
        SlotEntity slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found"));
        slot.setClient(client);
        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);
    }

    public void cancelBooking(String username, Integer slotId) {
        UserEntity client = getUser(username);
        SlotEntity slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found"));
        if (!slot.getClient().equals(client)) {
            throw new ApiException("You can only cancel your own bookings", HttpStatus.FORBIDDEN);
        }
        slot.setClient(null);
        slot.setStatus(SlotStatus.AVAILABLE);
        slotRepository.save(slot);
    }

    public List<SlotDto> getAvailableSlots(LocalDateTime startTime, LocalDateTime endTime, String doctorUsername,
            SlotStatus slotStatus, Integer specialtyId) {
        List<SlotEntity> slots = slotRepository.findByCriteria(startTime, endTime, doctorUsername, slotStatus,
                specialtyId);
        Set<String> doctorUsernames = slots.stream()
                .map(slot -> slot.getDoctor().getUsername())
                .collect(Collectors.toSet());
        Map<String, UserEntity> doctorMap = userRepository.findAllWithSpecialtiesByUsername(doctorUsernames)
                .stream()
                .collect(Collectors.toMap(UserEntity::getUsername, Function.identity()));
        return slots.stream()
                .map(slot -> SlotDto.from(slot, doctorMap.get(slot.getDoctor().getUsername())))
                .toList();
    }

    private SlotEntity saveSlotEntity(CreateSlotRangeDto createSlotRangeDto, int i, UserEntity doctor) {
        LocalDateTime start = calculateSlotStartTime(createSlotRangeDto, i);
        return slotRepository.save(SlotEntity.builder()
                .doctor(doctor)
                .startTime(start)
                .endTime(start.plus(createSlotRangeDto.getSlotDuration()))
                .status(SlotStatus.AVAILABLE)
                .build());
    }

    private LocalDateTime calculateSlotStartTime(CreateSlotRangeDto createSlotRangeDto, int i) {
        return createSlotRangeDto.getStartAvailability()
                .plusMinutes(i * createSlotRangeDto.getSlotDuration().toMinutes());
    }

    private int calculateTheNumberOfSlots(CreateSlotRangeDto createSlotRangeDto) {
        return (int) (Duration.between(createSlotRangeDto.getStartAvailability(),
                createSlotRangeDto.getEndAvailability()).toMinutes()
                / createSlotRangeDto.getSlotDuration().toMinutes());
    }

    private UserEntity getUser(String username) {
        return Optional.ofNullable(userRepository.findByUsername(username))
                .orElseThrow(EntityNotFoundException::new);
    }
}
