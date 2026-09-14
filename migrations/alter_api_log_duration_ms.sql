-- 批次2/O1：api_log 记录 LLM 调用耗时（毫秒），由 AdminLogService 聚合 avg/P95
USE `X`;

ALTER TABLE api_log
  ADD COLUMN duration_ms BIGINT NULL AFTER prompt_version;
