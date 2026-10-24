package com.saldev.turnovivo.repository;

import com.saldev.turnovivo.model.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface BusinessRepository extends JpaRepository<Business, UUID> {
    /*
    * Synchronized with BusinessService: "findBySlug(slug)"
    * Automatically generates at runtime:
    * SELECT * FROM businesses WHERE slug = ?
    */
    Optional<Business> findBySlug(String slug);

    Optional<Business> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    List<Business> findAllByOrderByCreatedAtAsc();

    List<Business> findAllByActiveTrue();
}
