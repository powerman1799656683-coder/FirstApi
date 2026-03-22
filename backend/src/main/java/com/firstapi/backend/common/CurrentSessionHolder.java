package com.firstapi.backend.common;

import com.firstapi.backend.model.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class CurrentSessionHolder {

    private static final ThreadLocal<AuthenticatedUser> CURRENT = new ThreadLocal<AuthenticatedUser>();

    private CurrentSessionHolder() {}

    public static void set(AuthenticatedUser user) {
        CURRENT.set(user);
    }

    public static AuthenticatedUser get() {
        return CURRENT.get();
    }

    public static AuthenticatedUser require() {
        AuthenticatedUser user = CURRENT.get();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
