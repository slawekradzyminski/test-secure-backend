package com.awesome.testing.controller.slot;

import com.awesome.testing.dto.slot.CreateSlotRangeDto;
import com.awesome.testing.dto.slot.SlotDto;
import com.awesome.testing.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/slots")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_DOCTOR')")
public class SlotController {

    private final SlotService slotService;

    @Operation(summary = "Create slots by providing availability",
            security = {@SecurityRequirement(name = "Authorization")})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<SlotDto> createSlots(@RequestBody @Valid CreateSlotRangeDto createSlotRangeDto) {
        return slotService.createSlots(createSlotRangeDto);
    }

}