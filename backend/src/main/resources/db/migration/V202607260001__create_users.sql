CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    nickname VARCHAR(100) NULL,
    library_name VARCHAR(200) NULL,
    department VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);
