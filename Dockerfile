FROM eclipse-temurin:23-jdk

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Копируем локально собранный JAR-файл в контейнер
COPY target/task-manager-bot-0.5-DEMO.jar app.jar

# Открываем порт приложения (если используется 8080)
EXPOSE 8080

ENV TZ=Europe/Moscow

# Точка входа — запуск JAR
CMD ["java", "-jar", "app.jar"]