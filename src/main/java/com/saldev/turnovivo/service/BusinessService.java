package com.saldev.turnovivo.service;

import com.saldev.turnovivo.dto.request.BusinessCreateRequest;
import com.saldev.turnovivo.dto.request.BusinessUpdateRequest;
import com.saldev.turnovivo.dto.response.BusinessResponse;
import com.saldev.turnovivo.dto.response.BusinessAdminResponse;
import com.saldev.turnovivo.exception.BusinessException;
import com.saldev.turnovivo.exception.ResourceNotFoundException;
import com.saldev.turnovivo.mapper.BusinessMapper;
import com.saldev.turnovivo.model.entity.Business;
import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.repository.BusinessRepository;
import com.saldev.turnovivo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
public class BusinessService {
    private final BusinessRepository businessRepository;
    private final BusinessMapper businessMapper;
    private final SlotCalendarService slotCalendarService;
    private final UserRepository userRepository;

    public BusinessService(
            BusinessRepository businessRepository,
            BusinessMapper businessMapper,
            SlotCalendarService slotCalendarService,
            UserRepository userRepository
    ) {
        this.businessRepository = businessRepository;
        this.businessMapper = businessMapper;
        this.slotCalendarService = slotCalendarService;
        this.userRepository = userRepository;
    }

    // Create a new business.
    // Validates slug uniqueness to protect clean URL routing.
    @Transactional
    public BusinessAdminResponse create(BusinessCreateRequest request) {
        String slug = normalizeSlug(request.getSlug());
        if (businessRepository.existsBySlug(slug)) {
            throw new BusinessException(
                    "A company owner must have the ADMIN role.",
                    HttpStatus.CONFLICT
            );
        }

        request.setSlug(slug);
        Business business = businessMapper.toEntity(request, requireAdminOwner(request.getOwnerId()));
        Business savedBusiness = businessRepository.saveAndFlush(business);
        slotCalendarService.reconcileBusiness(savedBusiness);

        return businessMapper.toAdminResponse(savedBusiness);
    }

    // Retrieves a single business contract by its slug identifier
    @Transactional(readOnly = true)
    public BusinessResponse findBySlug(String slug) {
        Business business = businessRepository.findBySlugAndActiveTrue(normalizeSlug(slug))
                .orElseThrow(() -> new ResourceNotFoundException("Business with slug '" + slug + "' not found."));
        return businessMapper.toResponse(business);
    }

    @Transactional(readOnly = true)
    public List<BusinessAdminResponse> findAll() {
        return businessRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(businessMapper::toAdminResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessAdminResponse findById(UUID id) {
        return businessMapper.toAdminResponse(requireBusiness(id));
    }

    @Transactional
    public BusinessAdminResponse update(UUID id, BusinessUpdateRequest request) {
        Business business = requireBusiness(id);
        String slug = normalizeSlug(request.getSlug());
        if (businessRepository.existsBySlugAndIdNot(slug, id)) {
            throw new BusinessException("There is already a company with this slug", HttpStatus.CONFLICT);
        }

        business.setName(request.getName().trim());
        business.setSlug(slug);
        business.setOwner(requireAdminOwner(request.getOwnerId()));
        if (request.getActive() != null) {
            business.setActive(request.getActive());
        }
        Business updatedBusiness = businessRepository.save(business);
        return businessMapper.toAdminResponse(updatedBusiness);
    }

    // Deletes a business from the platform database.
    @Transactional
    public void delete(UUID id) {
        Business business = requireBusiness(id);
        if (business.isActive()) {
            business.setActive(false);
            businessRepository.save(business);
        }
    }

    private Business requireBusiness(UUID id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("The requested company does not exist."));
    }

    private User requireAdminOwner(UUID ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("The requested owner user does not exist."));
        if (owner.getRole() != Role.ADMIN) {
            throw new BusinessException("A company owner must have the ADMIN role.", HttpStatus.BAD_REQUEST);
        }
        return owner;
    }

    private String normalizeSlug(String slug) {
        return slug.trim().toLowerCase();
    }
}
