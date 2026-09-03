-- migrations/create_notice_read_table.sql
-- 描述：创建用户通知阅读记录表，实现每用户独立的已读/未读状态
-- 日期：2026-07-13
-- 已备份 mysql -u root -p X < migrations/create_notice_read_table.sql

USE X;

CREATE TABLE IF NOT EXISTS notice_read (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    user_id INT NOT NULL COMMENT '用户 ID',
    notice_id INT NOT NULL COMMENT '通知 ID',
    read_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (notice_id) REFERENCES notice(notice_id) ON DELETE CASCADE,
    UNIQUE INDEX idx_user_notice (user_id, notice_id) COMMENT '防止重复记录',
    INDEX idx_notice_id (notice_id) COMMENT '用于管理员统计已读人数'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知阅读记录表';
