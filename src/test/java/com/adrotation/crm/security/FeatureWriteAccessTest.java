package com.adrotation.crm.security;

import com.adrotation.crm.calendar.controller.CalendarController;
import com.adrotation.crm.calendar.service.CalendarService;
import com.adrotation.crm.crm.controller.DealController;
import com.adrotation.crm.crm.service.DealService;
import com.adrotation.crm.schedule.controller.ShiftController;
import com.adrotation.crm.schedule.service.ShiftService;
import com.adrotation.crm.user.enums.Permission;
import com.adrotation.crm.user.enums.Role;
import com.adrotation.crm.user.service.UserAccessPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureWriteAccessTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void onlySuperAdminCanMutateFeatures() {
        DealController deals = secure(new DealController(mock(DealService.class)));
        ShiftController shifts = secure(new ShiftController(mock(ShiftService.class)));
        CalendarController calendar = secure(new CalendarController(mock(CalendarService.class)));
        List<Runnable> mutations = List.of(
                () -> deals.create(null), () -> deals.update(1L, null),
                () -> shifts.create(null), () -> shifts.update(1L, null),
                () -> shifts.complete(1L, false), () -> shifts.propose(1L, null),
                () -> calendar.create(null), () -> calendar.update(1L, null), () -> calendar.delete(1L));
        for (Role role : Role.values()) {
            login(role);
            for (Runnable mutation : mutations) {
                if (role == Role.SUPER_ADMIN) assertDoesNotThrow(mutation::run);
                else assertThrows(AccessDeniedException.class, mutation::run, role.name());
            }
        }
        login(Role.MANAGER);
        assertDoesNotThrow(() -> deals.get(1L));
        assertDoesNotThrow(() -> shifts.get(1L));
        assertDoesNotThrow(() -> calendar.calendar(null, null));
    }

    @Test void permissionsMatchWritePolicyIncludingMultipleRoles() {
        UserAccessPolicy policy = new UserAccessPolicy();
        Set<Permission> writes = Set.of(Permission.EDIT_ALL_DEALS, Permission.EDIT_OWN_DEALS,
                Permission.EDIT_SHIFT, Permission.EDIT_CALENDAR);
        for (Role role : Role.values()) {
            if (role == Role.SUPER_ADMIN) assertTrue(policy.permissionsFor(role).containsAll(writes));
            else assertTrue(policy.permissionsFor(role).stream().noneMatch(writes::contains));
        }
        assertTrue(policy.permissionsFor(Set.of(Role.SUPER_ADMIN, Role.MANAGER)).containsAll(writes));
    }

    private void login(Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "", List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @SuppressWarnings("unchecked")
    private <T> T secure(T target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (T) factory.getProxy();
    }
}
