-- migrations/add_notice_feedback_columns.sql
-- 描述：为 notice 表新增定向/反馈相关字段，并将历史上已回复的反馈回填为反馈通知。
--       使反馈回复与系统通知统一走 notice + notice_read 一套已读/未读机制（方案 B）。
-- 日期：2026-07-13
-- 执行：mysql -u root -p000 X < migrations/add_notice_feedback_columns.sql
-- 说明：可重复执行（字段/索引/外键用存在性判断；回填用 feedback_id 唯一索引去重）。

USE X;

-- 1. 新增字段（带存在性判断，可重复执行）
SET @db = 'X';
SET @tbl = 'notice';

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE notice
       ADD COLUMN target_user_id INT NULL COMMENT ''定向接收用户ID，NULL=广播所有人'',
       ADD COLUMN feedback_id    INT NULL COMMENT ''关联来源反馈ID，NULL=系统通知'',
       ADD COLUMN feedback_time  DATETIME NULL COMMENT ''原反馈提交时间'',
       ADD COLUMN feedback_type  VARCHAR(20) NULL COMMENT ''suggestion/ui/bug/other'',
       ADD COLUMN reply          TEXT NULL COMMENT ''管理员回复内容''',
    'SELECT 1'  -- 字段已存在则跳过
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'target_user_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 索引与外键（动态判断，可重复执行）
-- 2.1 idx_target_user
SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE notice ADD INDEX idx_target_user (target_user_id)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND INDEX_NAME = 'idx_target_user'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.2 idx_feedback（唯一，用于回填/回复幂等去重）
SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE notice ADD UNIQUE INDEX idx_feedback (feedback_id)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND INDEX_NAME = 'idx_feedback'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.3 外键 fk_notice_feedback
SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE notice ADD CONSTRAINT fk_notice_feedback FOREIGN KEY (feedback_id) REFERENCES feedback(feedback_id) ON DELETE CASCADE',
    'SELECT 1')
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND CONSTRAINT_NAME = 'fk_notice_feedback'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 回填：历史已回复反馈 -> 一条定向反馈通知（feedback_id 唯一索引保证幂等）
INSERT INTO notice (title, content, admin_id, target_user_id, feedback_id, feedback_time, feedback_type, reply, created_time)
SELECT
  CASE f.type
    WHEN 'suggestion' THEN '建议反馈'
    WHEN 'ui'         THEN '界面反馈'
    WHEN 'bug'        THEN 'BUG反馈'
    ELSE '其他反馈'
  END AS title,
  f.content,
  f.user_id,                -- 暂无记录回复管理员，用反馈所有者占位
  f.user_id,                -- 定向发给反馈所有者
  f.feedback_id,
  COALESCE(f.feedback_time, f.answer_time, NOW()),
  f.type,
  f.answer,
  f.answer_time
FROM feedback f
WHERE f.answer IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM notice n WHERE n.feedback_id = f.feedback_id
  );

-- 4. 校验
SELECT 'notice 新增字段数' AS item, COUNT(*) AS cols
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl
  AND COLUMN_NAME IN ('target_user_id','feedback_id','feedback_time','feedback_type','reply');

SELECT '回填反馈通知数' AS item, COUNT(*) AS cnt FROM notice WHERE feedback_id IS NOT NULL;
