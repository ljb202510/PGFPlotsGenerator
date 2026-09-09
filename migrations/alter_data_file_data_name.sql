-- 迁移：data_file.data_name 列上限由 20 扩至 50（修复“Data too long for column 'data_name'”）
-- 执行：mysql -u root -p000 X < alter_data_file_data_name.sql
ALTER TABLE data_file MODIFY COLUMN data_name VARCHAR(50) NOT NULL;
