package com.finantrack.common.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Impede a API de subir em produção com segredos de exemplo, fracos ou com endereços sem HTTPS.
 * Falhar na inicialização é melhor do que descobrir o erro com usuários reais.
 */
@Component
@Profile("prod")
public class ProductionSettingsCheck {

    public ProductionSettingsCheck(
            FinanTrackProperties properties, @Value("${spring.datasource.password}") String dbPassword) {
        List<String> problems = ProductionSettings.problems(properties, dbPassword);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Configuração de produção inválida:\n - " + String.join("\n - ", problems));
        }
    }
}
