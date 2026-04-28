package com.lza.aethercare.common.security;

import org.springframework.core.annotation.AliasFor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 簡寫 {@code @AuthenticationPrincipal AppUserDetails currentUser}：取出目前登入使用者。 */
@Target({ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface CurrentUser {

    @AliasFor(annotation = AuthenticationPrincipal.class, attribute = "errorOnInvalidType")
    boolean errorOnInvalidType() default false;
}
