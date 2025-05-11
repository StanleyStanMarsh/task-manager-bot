package ru.spbstu.hsai.config;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import ru.spbstu.hsai.infrastructure.MongoProperties;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@ComponentScan(basePackages = "ru.spbstu.hsai")
@Configuration
@EnableReactiveMongoRepositories(
        basePackages = {
                "ru.spbstu.hsai.usermanagement.repository",
                "ru.spbstu.hsai.simpletaskmanagment.repository",
                "ru.spbstu.hsai.repeatingtaskmanagment.repository"
        }
)
//@PropertySource("classpath:mongo.properties")
//@PropertySource("classpath:superadmin.properties")
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @Bean
    public MongoProperties mongoProperties(
            @Value("${mongo.host}") String host,
            @Value("${mongo.port}") int port,
            @Value("${mongo.database}") String database,
            @Value("${mongo.username}") String username,
            @Value("${mongo.password}") String password) {

        log.info("Mongo settings: {}:{}/{} (user: {})", host, port, database, username.isEmpty() ? "none" : username);
        return new MongoProperties(host, port, database, username, password);
    }

    @Bean
    public MongoClient reactiveMongoClient(MongoProperties props) {
        String uri;
        if (!props.username().isEmpty() && !props.password().isEmpty()) {
            uri = String.format("mongodb://%s:%s@%s:%d/%s?authSource=admin",
                    encodeURIComponent(props.username()),
                    encodeURIComponent(props.password()),
                    props.host(),
                    props.port(),
                    props.database());
        } else {
            uri = String.format("mongodb://%s:%d/%s",
                    props.host(),
                    props.port(),
                    props.database());
        }
        log.info("Creating MongoClient with URI: {}", uri.replaceFirst(":([^@]+)@", ":*****@"));
        return MongoClients.create(uri);
    }

    private String encodeURIComponent(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to encode MongoDB credentials", e);
        }
    }

    @Bean
    public ReactiveMongoDatabaseFactory reactiveMongoDatabaseFactory(MongoClient client, MongoProperties props) {
        return new SimpleReactiveMongoDatabaseFactory(client, props.database());
    }

    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(ReactiveMongoDatabaseFactory factory) {
        return new ReactiveMongoTemplate(factory);
    }
}