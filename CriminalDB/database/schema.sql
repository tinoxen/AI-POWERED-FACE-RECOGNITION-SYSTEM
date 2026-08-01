-- Reference schema for FaceDB.
-- Hibernate (ddl-auto=update) will create/evolve these tables automatically
-- when the backend starts. This file is provided for documentation and for
-- setting up the database/user manually if you prefer.

CREATE DATABASE IF NOT EXISTS facedb CHARACTER SET utf8mb4;

CREATE USER IF NOT EXISTS 'facedb_user'@'localhost' IDENTIFIED BY 'change_me';
GRANT ALL PRIVILEGES ON facedb.* TO 'facedb_user'@'localhost';
FLUSH PRIVILEGES;

USE facedb;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,      -- ADMIN, OFFICER, VIEWER
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS persons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    date_of_birth DATE,
    address VARCHAR(500),
    other_details TEXT,
    photo_path VARCHAR(500),
    face_embedding TEXT,             -- placeholder biometric data (see PersonService)
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    created_by VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,     -- LOGIN, VIEW_PERSON, CREATE_PERSON, UPDATE_PERSON, DELETE_PERSON, ...
    target_person_id BIGINT,
    details VARCHAR(1000),
    ip_address VARCHAR(45),
    timestamp DATETIME NOT NULL
    -- Intentionally no UPDATE/DELETE grants for the app's DB user on this
    -- table in a production setup, to keep the audit trail append-only.
);

CREATE INDEX idx_persons_full_name ON persons (full_name);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs (timestamp);
