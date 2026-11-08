package com.finantrack.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Mínimo de 8 caracteres e máximo de 72 bytes, o limite do BCrypt. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPasswordValidator.class)
public @interface ValidPassword {

    String message() default "A senha deve ter pelo menos 8 caracteres e no máximo 72 bytes.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
