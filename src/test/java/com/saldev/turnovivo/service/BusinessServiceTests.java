package com.saldev.turnovivo.service;

import com.saldev.turnovivo.dto.request.BusinessCreateRequest;
import com.saldev.turnovivo.dto.request.BusinessUpdateRequest;
import com.saldev.turnovivo.dto.response.BusinessAdminResponse;
import com.saldev.turnovivo.exception.BusinessException;
import com.saldev.turnovivo.mapper.BusinessMapper;
import com.saldev.turnovivo.model.entity.Business;
import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.repository.BusinessRepository;
import com.saldev.turnovivo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTests {

    @Mock private BusinessRepository businessRepository;
    @Mock private UserRepository userRepository;
    @Mock private SlotCalendarService slotCalendarService;

    private BusinessService service;

    @BeforeEach
    void setUp() {
        service = new BusinessService(
                businessRepository,
                new BusinessMapper(),
                slotCalendarService,
                userRepository
        );
    }

    @Test
    void createsBusinessForAdminOwnerAndGeneratesCalendar() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).role(Role.ADMIN).build();
        BusinessCreateRequest request = new BusinessCreateRequest("Barbería Centro", "barberia-centro", ownerId);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(businessRepository.existsBySlug("barberia-centro")).thenReturn(false);
        when(businessRepository.saveAndFlush(any(Business.class))).thenAnswer(invocation -> {
            Business business = invocation.getArgument(0);
            business.setId(UUID.randomUUID());
            business.setCreatedAt(ZonedDateTime.now());
            return business;
        });

        BusinessAdminResponse response = service.create(request);

        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.active()).isTrue();
        verify(slotCalendarService).reconcileBusiness(any(Business.class));
    }

    @Test
    void rejectsDuplicateSlugBeforeResolvingOwnerOrGeneratingCalendar() {
        UUID ownerId = UUID.randomUUID();
        when(businessRepository.existsBySlug("barberia-centro")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new BusinessCreateRequest("Barbería Centro", "BARBERIA-CENTRO", ownerId)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT))
                .hasMessage("Ya existe una empresa con este slug.");

        verify(userRepository, never()).findById(any(UUID.class));
        verify(businessRepository, never()).saveAndFlush(any(Business.class));
        verify(slotCalendarService, never()).reconcileBusiness(any(Business.class));
    }

    @Test
    void rejectsNonAdminOwner() {
        UUID ownerId = UUID.randomUUID();
        when(businessRepository.existsBySlug("barberia-centro")).thenReturn(false);
        when(userRepository.findById(ownerId))
                .thenReturn(Optional.of(User.builder().id(ownerId).role(Role.CUSTOMER).build()));

        assertThatThrownBy(() -> service.create(
                new BusinessCreateRequest("Barbería Centro", "barberia-centro", ownerId)
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void updatesSlugAndOwnerWithoutRecreatingCalendar() {
        UUID businessId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).role(Role.ADMIN).build();
        Business business = business(businessId, owner);
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(businessRepository.save(business)).thenReturn(business);

        BusinessAdminResponse response = service.update(
                businessId,
                new BusinessUpdateRequest("Nombre Nuevo", "nombre-nuevo", ownerId, null)
        );

        assertThat(response.slug()).isEqualTo("nombre-nuevo");
        assertThat(response.name()).isEqualTo("Nombre Nuevo");
    }

    @Test
    void deletionIsLogicalAndIdempotent() {
        UUID businessId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        Business business = business(businessId, owner);
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

        service.delete(businessId);
        service.delete(businessId);

        assertThat(business.isActive()).isFalse();
        verify(businessRepository).save(business);
    }

    private Business business(UUID id, User owner) {
        return Business.builder()
                .id(id)
                .name("Empresa")
                .slug("empresa")
                .owner(owner)
                .active(true)
                .createdAt(ZonedDateTime.now())
                .build();
    }
}
