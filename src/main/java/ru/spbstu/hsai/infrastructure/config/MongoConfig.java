package ru.spbstu.hsai.infrastructure.config;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import ru.spbstu.hsai.infrastructure.db.MongoProperties;

@ComponentScan(basePackages = "ru.spbstu.hsai")
@Configuration
@EnableReactiveMongoRepositories(
        basePackages = "ru.spbstu.hsai.modules.usermanagement.repository"
)
@PropertySource("classpath:mongo.properties")
@PropertySource("classpath:superadmin.properties")
public class MongoConfig {

    @Bean
    public MongoProperties mongoProperties(
            @Value("${mongo.host}") String host,
            @Value("${mongo.database}") String database) {
        return new MongoProperties(host, database);
    }

    @Bean
    public MongoClient reactiveMongoClient(MongoProperties props) {
        return MongoClients.create(props.host());
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