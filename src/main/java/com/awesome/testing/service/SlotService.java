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
import java.util.Optional;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final SlotRepository slotRepository;
    private final UserRepository userRepository;

    public List<SlotDto> createSlots(CreateSlotRangeDto createSlotRangeDto) {
        UserEntity doctor = getUser(createSlotRangeDto.getUsername());

        if (slotRepository.existsByDoctorAndStartTimeBetween(doctor, createSlotRangeDto.getStartAvailability(), createSlotRangeDto.getEndAvailability())) {
            throw new ApiException("Slots already exist in the provided time range", HttpStatus.BAD_REQUEST);
        }

        return IntStream.range(0, calculateTheNumberOfSlots(createSlotRangeDto))
                .mapToObj(i -> {
                    LocalDateTime start = calculateSlotStartTime(createSlotRangeDto, i);
                    return slotRepository.save(SlotEntity.builder()
                            .doctor(doctor)
                            .startTime(start)
                            .endTime(start.plus(createSlotRangeDto.getSlotDuration()))
                            .status(SlotStatus.AVAILABLE)
                            .build());
                })
                .map(SlotDto::from)
                .toList();
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

    public SlotEntity bookSlot(String username, Integer slotId) {
        UserEntity client = getUser(username);
        SlotEntity slot = slotRepository.findById(slotId).orElseThrow(() -> new RuntimeException("Slot not found"));
        slot.setClient(client);
        slot.setStatus(SlotStatus.BOOKED);
        return slotRepository.save(slot);
    }

    private UserEntity getUser(String username) {
        return Optional.ofNullable(userRepository.findByUsername(username))
                .orElseThrow(EntityNotFoundException::new);
    }
}
