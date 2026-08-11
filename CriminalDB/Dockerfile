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

# New records are validated and embedded with the bundled OpenCV script.
# The runtime image must therefore include Python and its image-processing
# dependencies; the JRE base image does not provide them.
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-pip \
    && python3 -m pip install --no-cache-dir numpy opencv-python-headless \
    && rm -rf /var/lib/apt/lists/*

# Copy built JAR
COPY --from=build /workspace/backend/target/*.jar ./app.jar

# Copy frontend
COPY frontend ./frontend

# Keep the face-embedding script at the path used by the application.
COPY backend/scripts ./scripts

# Fetch recognition models while building the image. This prevents a record
# submission from failing or timing out while it waits for a model download.
RUN python3 -c 'import os, urllib.request; os.makedirs("/app/scripts/models", exist_ok=True); urllib.request.urlretrieve("https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx", "/app/scripts/models/face_detection_yunet_2023mar.onnx"); urllib.request.urlretrieve("https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx", "/app/scripts/models/face_recognition_sface_2021dec.onnx")'

# Tell Spring Boot where the frontend is
ENV APP_STATIC_DIR=/app/frontend

# Render provides the PORT environment variable
ENV PORT=10000

EXPOSE 10000

# Start Spring Boot
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
