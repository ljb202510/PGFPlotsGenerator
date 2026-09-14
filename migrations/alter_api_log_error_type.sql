-- 批次3/E2：api_log 记录失败分类，供管理端聚合「失败归类」计数
USE `X`;

ALTER TABLE api_log
  ADD COLUMN error_type VARCHAR(32) NULL AFTER duration_ms;
