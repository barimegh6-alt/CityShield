package com.cityshield.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One required resource line of an incident. */
public record RequirementReq(@NotNull Long resourceTypeId, @NotNull @Min(1) Integer quantity) {
}
