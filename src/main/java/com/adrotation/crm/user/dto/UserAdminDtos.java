package com.adrotation.crm.user.dto;

import com.adrotation.crm.user.enums.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public final class UserAdminDtos {
    private UserAdminDtos() {}

    public record RoleAssignmentRequest(@NotEmpty Set<Role> roles) {}
}
