package com.saldev.turnovivo.controller;

import com.saldev.turnovivo.dto.request.BookAppointmentRequest;
import com.saldev.turnovivo.dto.response.AppointmentResponse;
import com.saldev.turnovivo.dto.response.MessageResponse;
import com.saldev.turnovivo.dto.response.SlotResponse;
import com.saldev.turnovivo.dto.response.WaitlistResponse;
import com.saldev.turnovivo.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/business/{businessId}/available")
    public List<SlotResponse> getAvailableSlots(@PathVariable UUID businessId) {
        return appointmentService.findAvailableSlots(businessId);
    }

    @PostMapping("/book")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse bookAppointment(
            @Valid @RequestBody BookAppointmentRequest request,
            @AuthenticationPrincipal String customerId
    ) {
        return appointmentService.bookAppointment(request.slotId(), UUID.fromString(customerId));
    }

    @PostMapping("/waitlist/join")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public WaitlistResponse joinWaitlist(
            @Valid @RequestBody BookAppointmentRequest request,
            @AuthenticationPrincipal String customerId
    ) {
        return appointmentService.joinWaitlist(request.slotId(), UUID.fromString(customerId));
    }

    @PostMapping("/{appointmentId}/cancel")
    public MessageResponse cancelAppointment(
            @PathVariable UUID appointmentId,
            @AuthenticationPrincipal String customerId
    ) {
        return new MessageResponse(
                    appointmentService.cancelAppointment(appointmentId, UUID.fromString(customerId))
        );
    }

    @PostMapping("/slots/{slotId}/confirm-hold")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse confirmHold(
            @PathVariable UUID slotId,
            @AuthenticationPrincipal String customerId
    ) {
        return appointmentService.confirmHold(slotId, UUID.fromString(customerId));
    }

    @PostMapping("/slots/{slotId}/decline-hold")
    @PreAuthorize("hasRole('CUSTOMER')")
    public MessageResponse declineHold(
            @PathVariable UUID slotId,
            @AuthenticationPrincipal String customerId
    ) {
        return new MessageResponse(
                    appointmentService.declineHold(slotId, UUID.fromString(customerId))
        );
    }

    @PostMapping("/slots/{slotId}/leave-waitlist")
    @PreAuthorize("hasRole('CUSTOMER')")
    public MessageResponse leaveWaitlist(
            @PathVariable UUID slotId,
            @AuthenticationPrincipal String customerId
    ) {
        return new MessageResponse(
                    appointmentService.leaveWaitlist(slotId, UUID.fromString(customerId))
        );
    }
}
