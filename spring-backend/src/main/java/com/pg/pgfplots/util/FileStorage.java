package com.pg.pgfplots.util;

import com.pg.pgfplots.config.AppProperties;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传文件路径工具。
 * <p>延续 Node 的存储约定：new 文件落盘到配置的 uploads 目录，DB 中 file_path 记为
 * {@code uploads/<文件名>} 相对路径，读取时按文件名解析回目录。</p>
 */
@Component
public class FileStorage {

    /** uploads 目录绝对路径 */
    @Getter
    private final Path uploadsDir;

    public FileStorage(AppProperties appProperties) {
        this.uploadsDir = Paths.get(appProperties.getStorage().getUploads()).toAbsolutePath().normalize();
    }

    /** 生成唯一文件名：时间戳-随机数+原扩展名（与 Node 一致）。 */
    public String newFilename(String originalName) {
        String ext = "";
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) {
                ext = originalName.substring(dot);
            }
        }
        return System.currentTimeMillis() + "-" + Math.round(Math.random() * 1e9) + ext;
    }

    /** 数据库存储的相对路径。 */
    public String storedPath(String filename) {
        return "uploads/" + filename;
    }

    /**
     * 把数据库中的 file_path 解析为绝对路径。
     * <p>兼容历史数据：{@code uploads/xxx} 视为相对 uploads 目录；绝对路径原样使用。</p>
     */
    public Path resolveStored(String storedPath) {
        if (storedPath == null) {
            return null;
        }
        Path p = Paths.get(storedPath);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        String normalized = storedPath.replace('\\', '/');
        if (normalized.startsWith("uploads/")) {
            normalized = normalized.substring("uploads/".length());
        }
        return uploadsDir.resolve(normalized).normalize();
    }
}
