package com.cityshield.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Body of POST /incidents and PUT /incidents/{id}. */
public record IncidentRequest(
        @NotBlank String category,
        @NotNull Long zoneId,
        @NotBlank @Size(min = 5, max = 500) String description,
        @NotNull @Min(1) @Max(5) Integer severity,
        @NotNull @Min(1) @Max(5) Integer urgency,
        @NotNull @Min(0) @Max(1000000) Integer affectedPeople,
        @Valid List<RequirementReq> requirements) {
}
