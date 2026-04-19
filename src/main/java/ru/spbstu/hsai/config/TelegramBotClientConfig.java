package ru.spbstu.hsai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.telegram.telegrambots.bots.DefaultBotOptions;

/**
 * Опциональный прокси для Telegram Bot API (исходящий HTTPS).
 * <p>
 * Переменные окружения (в Pod / docker-compose):
 * <ul>
 *   <li>{@code TELEGRAM_PROXY_HOST} — хост прокси. Из пода minikube на Mac: {@code host.minikube.internal}
 *       (или {@code host.docker.internal} в Docker Desktop), не {@code 127.0.0.1}.</li>
 *   <li>{@code TELEGRAM_PROXY_PORT} — порт (например HTTP 10809 или SOCKS5 10808).</li>
 *   <li>{@code TELEGRAM_PROXY_TYPE} — {@code HTTP}, {@code SOCKS5} или {@code SOCKS4} (по умолчанию HTTP).</li>
 * </ul>
 * На машине с Xray/V2Ray inbound должен слушать не только loopback, иначе с контейнера не подключиться:
 * {@code "listen": "0.0.0.0"} для нужного inbound.
 */
@Configuration
public class TelegramBotClientConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotClientConfig.class);

    @Bean
    public DefaultBotOptions defaultBotOptions(Environment env) {
        DefaultBotOptions options = new DefaultBotOptions();
        String host = trimToNull(env.getProperty("TELEGRAM_PROXY_HOST"));
        String portRaw = trimToNull(env.getProperty("TELEGRAM_PROXY_PORT"));
        if (host == null || portRaw == null) {
            return options;
        }
        int port;
        try {
            port = Integer.parseInt(portRaw);
        } catch (NumberFormatException e) {
            log.warn("TELEGRAM_PROXY_PORT is not a number: {}", portRaw);
            return options;
        }
        if (port <= 0 || port > 65535) {
            log.warn("TELEGRAM_PROXY_PORT out of range: {}", port);
            return options;
        }
        String typeRaw = trimToNull(env.getProperty("TELEGRAM_PROXY_TYPE"));
        DefaultBotOptions.ProxyType proxyType = DefaultBotOptions.ProxyType.HTTP;
        if (typeRaw != null) {
            proxyType = switch (typeRaw.trim().toUpperCase()) {
                case "SOCKS5" -> DefaultBotOptions.ProxyType.SOCKS5;
                case "SOCKS4" -> DefaultBotOptions.ProxyType.SOCKS4;
                case "HTTP" -> DefaultBotOptions.ProxyType.HTTP;
                default -> {
                    log.warn("Unknown TELEGRAM_PROXY_TYPE {}, using HTTP", typeRaw);
                    yield DefaultBotOptions.ProxyType.HTTP;
                }
            };
        }
        options.setProxyType(proxyType);
        options.setProxyHost(host);
        options.setProxyPort(port);
        log.info("Telegram API proxy: {} {}:{}", proxyType, host, port);
        return options;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
