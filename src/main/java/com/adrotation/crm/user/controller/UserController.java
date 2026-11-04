package com.adrotation.crm.user.controller;

import com.adrotation.crm.auth.dto.AuthDtos;
import com.adrotation.crm.user.dto.UserAdminDtos;
import com.adrotation.crm.user.enums.Role;
import com.adrotation.crm.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROK')")
    public List<AuthDtos.UserView> list(@RequestParam(required = false) Role role) {
        return userService.list(role);
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROK')")
    public AuthDtos.UserView assignRoles(
            @PathVariable Long id,
            @Valid @RequestBody UserAdminDtos.RoleAssignmentRequest request
    ) {
        return userService.assignRoles(id, request.roles());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AuthDtos.UserView disable(@PathVariable Long id) {
        return userService.disable(id);
    }
}
