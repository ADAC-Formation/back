package com.adac.portail.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PATCH /api/users/me — all fields optional (see docs/tech.md). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    private Boolean emailNotificationsEnabled;
}
