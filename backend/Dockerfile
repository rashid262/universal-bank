FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# Resolve dependencies first so code-only changes reuse this cached layer
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q clean package -DskipTests

# Runtime needs only a JRE, which keeps the image small
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Run as a non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

EXPOSE 8080

# Sized for small instances such as Render's 512 MB free tier; PORT is read by application.yml
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
