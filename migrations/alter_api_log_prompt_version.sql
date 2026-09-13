-- 批次1/A2：api_log 记录提示词版本，支持「同一用例换版本对比」
USE `X`;

ALTER TABLE api_log
  ADD COLUMN prompt_version VARCHAR(32) NULL AFTER call_error;
