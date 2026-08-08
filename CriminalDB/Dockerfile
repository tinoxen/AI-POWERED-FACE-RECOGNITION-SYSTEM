FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY backend/pom.xml backend/
COPY backend/mvnw backend/
COPY backend/.mvn backend/.mvn
COPY backend/src backend/src

RUN chmod +x backend/mvnw
RUN ./backend/mvnw -f backend/pom.xml clean package -DskipTests


FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /workspace/backend/target/*.jar /app/app.jar
COPY frontend /app/frontend

ENV APP_STATIC_DIR=/app/frontend

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
