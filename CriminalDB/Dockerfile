FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/
COPY backend/mvnw backend/
COPY backend/.mvn backend/.mvn
COPY backend/src backend/src
COPY frontend frontend

RUN chmod +x backend/mvnw && ./backend/mvnw -f backend/pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /workspace/backend/target/facedb-*.jar /app/app.jar
COPY frontend /app/frontend

ENV PORT=10000 \
    APP_STATIC_DIR=/app/frontend \
    JAVA_OPTS=""

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
