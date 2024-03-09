package com.awesome.testing.controller.slot;

import com.awesome.testing.controller.utils.authorization.OperationWithSecurity;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForAllRoles;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForClient;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForDoctorAndAdmin;
import com.awesome.testing.dto.slot.CreateSlotRangeDto;
import com.awesome.testing.dto.slot.SlotDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.entities.user.UserEntity;
import com.awesome.testing.exception.ApiException;
import com.awesome.testing.service.SlotService;
import com.awesome.testing.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = { "http://localhost:8081", "http://127.0.0.1:8081" }, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;
    private final UserService userService;

    @PreAuthorizeForAllRoles
    @OperationWithSecurity(summary = "Get available slots")
    @GetMapping
    public List<SlotDto> getAvailableSlots(@Valid SlotSearchCriteria criteria) {
        return slotService.getAvailableSlots(criteria.getStartTime(), criteria.getEndTime(),
                criteria.getDoctorUsername(), criteria.getSlotStatus(), criteria.getSpecialtyId());
    }

    @PreAuthorizeForDoctorAndAdmin
    @OperationWithSecurity(summary = "Create slots by providing availability")
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

    @PreAuthorizeForClient
    @OperationWithSecurity(summary = "Book a slot")
    @PutMapping("/{slotId}/book")
    public void bookSlot(@PathVariable Integer slotId, HttpServletRequest req) {
        String currentUsername = req.getRemoteUser();
        slotService.bookSlot(currentUsername, slotId);
    }

    @PreAuthorizeForClient
    @OperationWithSecurity(summary = "Get booked slots by client")
    @GetMapping("/booked")
    public List<SlotDto> getBookedSlots(HttpServletRequest req) {
        String currentUsername = req.getRemoteUser();
        return slotService.getBookedSlots(currentUsername);
    }

    @PreAuthorizeForClient
    @OperationWithSecurity(summary = "Cancel a booking")
    @PutMapping("/{slotId}/cancel")
    public void cancelBooking(@PathVariable Integer slotId, HttpServletRequest req) {
        String currentUsername = req.getRemoteUser();
        slotService.cancelBooking(currentUsername, slotId);
    }

}