package com.saldev.turnovivo.controller;

import com.saldev.turnovivo.dto.request.BusinessCreateRequest;
import com.saldev.turnovivo.dto.request.BusinessUpdateRequest;
import com.saldev.turnovivo.dto.response.BusinessResponse;
import com.saldev.turnovivo.dto.response.BusinessAdminResponse;
import com.saldev.turnovivo.service.BusinessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/businesses")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    /*
     * REST CONTRACT: Creates a new business profile.
     * ACCESSIBILITY: Fully open to anonymous or public web traffic.
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessAdminResponse create(@Valid @RequestBody BusinessCreateRequest request) {
        return businessService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<BusinessAdminResponse> findAll() {
        return businessService.findAll();
    }

    @GetMapping("/id/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public BusinessAdminResponse findById(@PathVariable UUID id) {
        return businessService.findById(id);
    }

    /*
     * REST CONTRACT: Public endpoint to fetch business metadata by its unique URL slug.
     * ACCESSIBILITY: Fully open to anonymous or public web traffic.
     */
    @GetMapping("/{slug}")
    @ResponseStatus(HttpStatus.OK)
    public BusinessResponse findBySlug(@PathVariable("slug") String slug) {
        return businessService.findBySlug(slug);
    }

    /*
     * REST CONTRACT: Updates an existing business profile name using its primary key.
     * ENTERPRISE PERIMETER: Restricts write operations stricty to system administrators
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public BusinessAdminResponse update(@PathVariable("id") UUID id, @Valid @RequestBody BusinessUpdateRequest request) {
        return businessService.update(id, request);
    }

    /*
     * REST CONTRACT: Hard-deletes a business profile hierachy from the database repository.
     * RETURN TYPE: 204 No Content for clean entity teardowns.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        businessService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
