# 🔴 DEV-07 : image complète non officielle au lieu de eclipse-temurin:21-jre-alpine
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# 🔴 DEV-07 : image complète au lieu de JRE alpine minimale
FROM openjdk:21

# 🔴 DEV-08 : pas de USER — tourne en root
WORKDIR /app

# 🔴 DEV-09 : pas de .dockerignore — COPY . . copie .env, target/, etc.
COPY --from=build /app/target/*.jar app.jar

# 🔴 : expose le port de debug JVM
EXPOSE 8080 5005

# 🔴 DEV-08 : -agentlib actif — port de debug JVM ouvert
CMD ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "app.jar"]
