package com.learning.airport.security;

import com.learning.airport.enums.Permission;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service(value = "authorizationService")
public class PreAuthorizeService {
    private static Map<String, List<Permission>> mapPermissionByUsername = new HashMap<>();

    static {
        mapPermissionByUsername.put("admin", List.of(Permission.EDIT, Permission.VIEW));
        mapPermissionByUsername.put("user", List.of(Permission.VIEW));
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated();

    }

    public static CustomUserDetails getUserDetails() {
        return isAuthenticated() ? (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal() : null;
    }

    public boolean hasPermission(Permission permission) {
        CustomUserDetails user = getUserDetails();
        assert user != null;
        String username = user.getUsername();
        return mapPermissionByUsername.get(username).contains(permission);
    }
}
