package com.finantrack.common.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Distingue a violação de unicidade das demais falhas de integridade.
 * Sem isso, uma chave estrangeira violada seria relatada como nome duplicado.
 *
 * <p>Usa o tipo derivado do SQLState (23505), e não o nome da restrição: o Hibernate
 * não extrai o nome quando o PostgreSQL responde em outro idioma. Use só em tabelas
 * com um único índice único além da chave primária.
 */
public final class UniqueConstraints {

    private UniqueConstraints() {
    }

    public static boolean isViolated(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getKind() == ConstraintKind.UNIQUE;
            }
        }
        return false;
    }
}
