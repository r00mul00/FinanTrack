package com.finantrack.card.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Divide em centavos exatos; a diferença do arredondamento vai para a primeira parcela. */
public final class InstallmentSplitter {

    private InstallmentSplitter() {
    }

    /** Retorna lista vazia se o total não comporta ao menos um centavo por parcela. */
    public static List<BigDecimal> split(BigDecimal total, int count) {
        BigDecimal regular = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        if (regular.signum() == 0) {
            return List.of();
        }
        BigDecimal first = total.subtract(regular.multiply(BigDecimal.valueOf(count - 1L)));

        List<BigDecimal> installments = new ArrayList<>(count);
        installments.add(first);
        for (int i = 1; i < count; i++) {
            installments.add(regular);
        }
        return installments;
    }
}
