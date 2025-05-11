FROM eclipse-temurin:23-jdk

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем локально собранный JAR-файл в контейнер
COPY target/task-manager-bot-0.5-DEMO.jar app.jar

# Открываем порт приложения (если используется 8080)
EXPOSE 8080

# Точка входа — запуск JAR
CMD ["java", "-jar", "app.jar"]