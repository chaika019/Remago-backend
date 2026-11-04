package com.adrotation.crm.user.service;

import com.adrotation.crm.user.enums.Permission;
import com.adrotation.crm.user.enums.Role;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class UserAccessPolicy {

    public Set<Permission> permissionsFor(Set<Role> roles) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        if (roles == null || roles.isEmpty()) {
            return result;
        }

        for (Role role : roles) {
            result.addAll(permissionsFor(role));
        }
        return result;
    }

    public Set<Permission> permissionsFor(Role role) {
        return switch (role) {
            case SUPER_ADMIN -> EnumSet.allOf(Permission.class);
            case DIRECTOR -> EnumSet.of(
                    Permission.VIEW_DASHBOARD,
                    Permission.VIEW_SCHEDULE,
                    Permission.VIEW_ALL_DEALS,
                    Permission.VIEW_NOTIFICATIONS
            );
            case ROP, ROK -> EnumSet.of(
                    Permission.VIEW_DASHBOARD,
                    Permission.VIEW_SCHEDULE,
                    Permission.EDIT_CITIES,
                    Permission.VIEW_ALL_DEALS,
                    Permission.UPLOAD_PHOTO_REPORT,
                    Permission.VIEW_NOTIFICATIONS,
                    Permission.MANAGE_USERS
            );
            case MANAGER -> EnumSet.of(
                    Permission.VIEW_DASHBOARD,
                    Permission.VIEW_SCHEDULE,
                    Permission.UPLOAD_PHOTO_REPORT,
                    Permission.VIEW_NOTIFICATIONS
            );
            case LAWYER -> EnumSet.of(
                    Permission.VIEW_DASHBOARD,
                    Permission.VIEW_SCHEDULE,
                    Permission.VIEW_NOTIFICATIONS
            );
            case SHIFT_WORKER -> EnumSet.of(
                    Permission.VIEW_DASHBOARD,
                    Permission.VIEW_SCHEDULE,
                    Permission.UPLOAD_PHOTO_REPORT,
                    Permission.VIEW_NOTIFICATIONS
            );
            case BUH, DELO -> EnumSet.of(
                    Permission.VIEW_DASHBOARD,
                    Permission.VIEW_SCHEDULE,
                    Permission.VIEW_NOTIFICATIONS,
                    Permission.VIEW_DOCUMENTS,
                    Permission.EDIT_DOCUMENTS
            );
            case PENDING -> EnumSet.noneOf(Permission.class);
        };
    }
}
