package ru.spbstu.hsai.infrastructure.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import ru.spbstu.hsai.infrastructure.db.MongoProperties;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@ComponentScan(basePackages = "ru.spbstu.hsai")
@Configuration
@EnableMongoRepositories(
        basePackages = "ru.spbstu.hsai.modules.usermanagement.repository"
)
@PropertySource("classpath:mongo.properties")
@PropertySource("classpath:superadmin.properties")
public class MongoConfig  {

    @Bean
    public MongoProperties mongoProperties(
            @Value("${mongo.host}") String host,
            @Value("${mongo.database}") String database) {
        return new MongoProperties(host, database);
    }

    @Bean
    public MongoClient mongoClient(MongoProperties props) {
        return MongoClients.create(props.host());
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient client, MongoProperties props) {
        return new SimpleMongoClientDatabaseFactory(client, props.database());
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory) {
        return new MongoTemplate(factory);
    }
}


