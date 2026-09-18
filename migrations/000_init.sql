DROP DATABASE IF EXISTS X;
CREATE DATABASE X;
USE X;

CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(10) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('user', 'admin') NOT NULL DEFAULT 'user', 
    register_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users (username, email, password, role) 
VALUES (
  'admin123',
  'admin@pgfplots.com',
  '$2b$10$kkbK9FE66BSuZt.zEyitl.G8WqkK2yUmusQ53HWkFKRzLq..4iuc2',
  'admin'
);

CREATE TABLE data_file (
    data_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,  
    data_name VARCHAR(50) NOT NULL,
    data_size INT NOT NULL,
    description TEXT,
    load_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    mimetype VARCHAR(100),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE generation_history (
    history_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,  
    data_id INT,     
    generation_description TEXT NOT NULL,
    generation_code TEXT,
    generation_path VARCHAR(500),
    generation_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (data_id) REFERENCES data_file(data_id) ON DELETE SET NULL
);

CREATE TABLE api_log (
    call_id  INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL, 
    history_id INT,
    call_status ENUM('success', 'failed') NOT NULL,
    call_time DATETIME NOT NULL,
    call_error TEXT,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (history_id) REFERENCES generation_history(history_id)
);

CREATE TABLE feedback (
    feedback_id INT AUTO_INCREMENT PRIMARY KEY, 
    user_id INT NOT NULL, 
    type ENUM('suggestion', 'ui', 'bug', 'other') NOT NULL, 
    content TEXT NOT NULL,
    feedback_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    answer TEXT,
    answer_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE system_log (
    sys_id INT AUTO_INCREMENT PRIMARY KEY,
    system_status ENUM('normal', 'warning', 'error') NOT NULL,
    log_time DATETIME NOT NULL,
    error TEXT
);

CREATE TABLE email_verification_codes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  code VARCHAR(6) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  INDEX idx_email (email),
  INDEX idx_expires (expires_at)
);

CREATE TABLE notice (
    notice_id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    admin_id INT, -- 发布通知的管理员ID，这里默认通知都是发给所有用户，所以没有用户id
    is_read BOOLEAN DEFAULT FALSE,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (admin_id) REFERENCES users(user_id)
);