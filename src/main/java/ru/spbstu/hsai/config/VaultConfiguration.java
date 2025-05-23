package ru.spbstu.hsai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.env.VaultPropertySource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

@Configuration
public class VaultConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VaultConfiguration.class);

    private final String vaultAddr = System.getenv("VAULT_ADDR");

    private String vaultToken;

    {
        try {
            vaultToken = Files.readString(Paths.get(System.getenv("VAULT_TOKEN_FILE"))).trim();
        } catch (IOException e) {
            log.error("error in vault", e);
        }
    }

    @Bean
    public VaultEndpoint vaultEndpoint() {
        log.info(vaultAddr);
        log.info(vaultToken);
        return VaultEndpoint.from(URI.create(vaultAddr));
    }
    @Bean
    public ClientAuthentication clientAuthentication() {
        return new TokenAuthentication(vaultToken);
    }
    @Bean
    public VaultTemplate vaultTemplate(VaultEndpoint endpoint,
                                       ClientAuthentication auth) {
        return new VaultTemplate(endpoint, auth);
    }
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer(
            ConfigurableEnvironment env,
            VaultTemplate vaultTemplate) {
        // читаем секреты из path=secret/task-manager-bot
        VaultPropertySource vps =
                new VaultPropertySource(vaultTemplate, "secret/task-manager-bot");
        env.getPropertySources().addFirst(vps);
        return new PropertySourcesPlaceholderConfigurer();
    }
}

