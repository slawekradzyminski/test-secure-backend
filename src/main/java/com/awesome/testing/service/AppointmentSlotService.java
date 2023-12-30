package com.awesome.testing.service;

import com.awesome.testing.entities.slot.AppointmentSlotEntity;
import com.awesome.testing.entities.slot.SlotStatus;
import com.awesome.testing.entities.user.UserEntity;
import com.awesome.testing.repository.AppointmentSlotRepository;
import com.awesome.testing.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppointmentSlotService {

    private final AppointmentSlotRepository appointmentSlotRepository;
    private final UserRepository userRepository;

    public AppointmentSlotEntity createSlot(String username, LocalDateTime startTime, LocalDateTime endTime) {
        UserEntity doctor = getUser(username);
        AppointmentSlotEntity slot = new AppointmentSlotEntity();
        slot.setDoctor(doctor);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);
        return appointmentSlotRepository.save(slot);
    }

    public AppointmentSlotEntity bookSlot(String username, Integer slotId) {
        UserEntity client = getUser(username);
        AppointmentSlotEntity slot = appointmentSlotRepository.findById(slotId).orElseThrow(() -> new RuntimeException("Slot not found"));
        slot.setClient(client);
        slot.setStatus(SlotStatus.BOOKED);
        return appointmentSlotRepository.save(slot);
    }

    private UserEntity getUser(String username) {
        return Optional.ofNullable(userRepository.findByUsername(username))
                .orElseThrow(EntityNotFoundException::new);
    }
}
