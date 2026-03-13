CREATE TABLE role (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_name UNIQUE (name)
);

CREATE TABLE permission (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    module VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_permission_module_code UNIQUE (module, code)
);

CREATE TABLE `user` (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(20) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    real_name VARCHAR(50),
    phone VARCHAR(11),
    status TINYINT NOT NULL DEFAULT 1,
    freeze_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    freeze_reason VARCHAR(500),
    freeze_expire_time TIMESTAMP,
    last_login_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT fk_user_role_id FOREIGN KEY (role_id) REFERENCES role (id)
);

CREATE TABLE role_permission (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    role_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_rp_role_id FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_rp_permission_id FOREIGN KEY (permission_id) REFERENCES permission (id)
);

CREATE TABLE password_history (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ph_user_id FOREIGN KEY (user_id) REFERENCES `user` (id)
);
