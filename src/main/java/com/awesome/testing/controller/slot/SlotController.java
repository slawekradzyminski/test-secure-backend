package com.awesome.testing.controller.slot;

import com.awesome.testing.dto.slot.CreateSlotRangeDto;
import com.awesome.testing.dto.slot.SlotDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.entities.user.UserEntity;
import com.awesome.testing.exception.ApiException;
import com.awesome.testing.service.SlotService;
import com.awesome.testing.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = { "http://localhost:8081", "http://127.0.0.1:8081" }, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;
    private final UserService userService;

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_DOCTOR')")
    @Operation(summary = "Create slots by providing availability", security = {
            @SecurityRequirement(name = "Authorization") })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<SlotDto> createSlots(@RequestBody @Valid CreateSlotRangeDto createSlotRangeDto,
            HttpServletRequest req) {
        String currentUsername = req.getRemoteUser();
        UserEntity currentUser = userService.search(currentUsername);

        if (currentUser.getRoles().contains(Role.ROLE_DOCTOR) &&
                !currentUsername.equals(createSlotRangeDto.getUsername())) {
            throw new ApiException("Doctors can only create slots for themselves", HttpStatus.FORBIDDEN);
        }

        if (currentUser.getRoles().contains(Role.ROLE_ADMIN)) {
            UserEntity targetUser = userService.search(createSlotRangeDto.getUsername());
            if (!targetUser.getRoles().contains(Role.ROLE_DOCTOR)) {
                throw new ApiException("Admins can only create slots for doctors", HttpStatus.BAD_REQUEST);
            }
        }

        return slotService.createSlots(createSlotRangeDto);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_CLIENT') or hasRole('ROLE_DOCTOR')")
    @Operation(summary = "Get available slots", security = { @SecurityRequirement(name = "Authorization") })
    @GetMapping
    public List<SlotDto> getAvailableSlots(@Valid SlotSearchCriteria criteria) {
        return slotService.getAvailableSlots(criteria.getStartTime(), criteria.getEndTime(),
                criteria.getDoctorUsername(), criteria.getSlotStatus(), criteria.getDoctorTypeId());
    }

    @PreAuthorize("hasRole('ROLE_CLIENT')")
    @Operation(summary = "Book a slot", security = { @SecurityRequirement(name = "Authorization") })
    @PutMapping("/{slotId}/book")
    public void bookSlot(@PathVariable Integer slotId, HttpServletRequest req) {
        String currentUsername = req.getRemoteUser();
        slotService.bookSlot(currentUsername, slotId);
    }

}