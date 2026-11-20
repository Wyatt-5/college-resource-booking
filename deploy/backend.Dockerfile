FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 10001 booking && adduser -D -u 10001 -G booking booking \
    && mkdir -p /app/data && chown booking:booking /app/data
WORKDIR /app
COPY --chown=booking:booking backend.jar /app/backend.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
