package com.saldev.turnovivo.dto.response;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BusinessResponse {
    private UUID id;
    private String name;
    private String slug;
    private LocalDateTime createdAt;
}
