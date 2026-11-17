package com.cityshield.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of PATCH /incidents/{id}/status. */
public record StatusRequest(@NotBlank String status, String note) {
}
