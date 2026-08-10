# ---------- BUILD STAGE ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Copy Maven configuration first
COPY backend/pom.xml ./backend/
COPY backend/.mvn ./backend/.mvn
COPY backend/mvnw ./backend/mvnw

# Copy source code
COPY backend/src ./backend/src

# Make Maven wrapper executable
RUN chmod +x ./backend/mvnw

# Build Spring Boot application
RUN ./backend/mvnw \
    -f ./backend/pom.xml \
    clean package \
    -DskipTests


# ---------- RUN STAGE ----------
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy built JAR
COPY --from=build /workspace/backend/target/*.jar ./app.jar

# Copy frontend
COPY frontend ./frontend

# Tell Spring Boot where the frontend is
ENV APP_STATIC_DIR=/app/frontend

# Render provides the PORT environment variable
ENV PORT=10000

EXPOSE 10000

# Start Spring Boot
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
