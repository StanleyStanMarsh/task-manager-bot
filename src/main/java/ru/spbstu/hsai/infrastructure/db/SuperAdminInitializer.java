package ru.spbstu.hsai.infrastructure.db;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import ru.spbstu.hsai.modules.usermanagement.model.User;

import javax.annotation.PostConstruct;

@Configuration
public class SuperAdminInitializer {

    private final MongoTemplate mongoTemplate;

    @Value("${superadmin.telegramId}")
    private Long telegramId;
    @Value("${superadmin.username}")
    private String username;
    @Value("${superadmin.firstName}")
    private String firstName;
    @Value("${superadmin.lastName}")
    private String lastName;

    public SuperAdminInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void ensureSuperAdmin() {
        System.out.println("telegramId: " + telegramId);
        System.out.println("MongoTemplate: " + mongoTemplate);
        Query query = Query.query(Criteria.where("telegramId").is(telegramId));
        Update update = new Update()
                .setOnInsert("telegramId", telegramId)
                .setOnInsert("role", "ADMIN")
                .setOnInsert("username", username)
                .setOnInsert("firstName", firstName)
                .setOnInsert("lastName", lastName);
        mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                User.class
        );
    }
}