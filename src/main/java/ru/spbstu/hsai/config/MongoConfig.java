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

@ComponentScan(basePackages = "ru.spbstu.hsai")
@Configuration
@EnableReactiveMongoRepositories(
        basePackages = {
                "ru.spbstu.hsai.usermanagement.repository",
                "ru.spbstu.hsai.simpletaskmanagment.repository",
                "ru.spbstu.hsai.repeatingtaskmanagment.repository"
        }
)
@PropertySource("classpath:mongo.properties")
@PropertySource("classpath:superadmin.properties")
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @Bean
    public MongoProperties mongoProperties(
            @Value("${mongo.host}") String host,
            @Value("${mongo.port}") int port,
            @Value("${mongo.database}") String database) {
        log.info("Mongo settings: {}:{}/{}", host, port, database);
        return new MongoProperties(host, port, database);
    }

    @Bean
    public MongoClient reactiveMongoClient(MongoProperties props) {
        String uri = String.format("mongodb://%s:%d/%s",
                props.host(), props.port(), props.database());
        return MongoClients.create(uri);
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