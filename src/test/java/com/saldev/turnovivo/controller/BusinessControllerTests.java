package com.saldev.turnovivo.controller;

import com.saldev.turnovivo.dto.response.BusinessAdminResponse;
import com.saldev.turnovivo.dto.response.BusinessResponse;
import com.saldev.turnovivo.security.JwtFilter;
import com.saldev.turnovivo.security.JwtProvider;
import com.saldev.turnovivo.security.SecurityConfig;
import com.saldev.turnovivo.security.SecurityErrorWriter;
import com.saldev.turnovivo.service.BusinessService;
import com.saldev.turnovivo.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessController.class)
@Import({SecurityConfig.class, SecurityErrorWriter.class, JwtFilter.class})
class BusinessControllerTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BusinessService businessService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private SessionService sessionService;

    @Test
    void anonymousUserCannotListBusinesses() throws Exception {
        mockMvc.perform(get("/businesses").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void anonymousUserCanReadActiveBusinessBySlug() throws Exception {
        UUID businessId = UUID.randomUUID();
        when(businessService.findBySlug("empresa")).thenReturn(
                new BusinessResponse(
                        businessId,
                        "Empresa",
                        "empresa",
                        LocalDateTime.now()
                )
        );

        mockMvc.perform(get("/businesses/empresa").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(businessId.toString()))
                .andExpect(jsonPath("$.slug").value("empresa"));
    }

    @Test
    void regularAdminCannotListGlobalBusinesses() throws Exception {
        mockMvc.perform(get("/businesses")
                        .with(user("admin").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void superAdminCanListBusinesses() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(businessService.findAll()).thenReturn(List.of(
                new BusinessAdminResponse(
                        businessId,
                        "Empresa",
                        "empresa",
                        ownerId,
                        "owner@turnovivo.com",
                        true,
                        LocalDateTime.now()
                )
        ));

        mockMvc.perform(get("/businesses")
                        .with(user("superadmin").roles("SUPER_ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(businessId.toString()))
                .andExpect(jsonPath("$[0].ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$[0].active").value(true));
    }
}
