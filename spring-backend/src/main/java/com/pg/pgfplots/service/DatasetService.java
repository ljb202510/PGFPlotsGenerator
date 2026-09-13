package com.pg.pgfplots.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.dto.dataset.DatasetVO;
import com.pg.pgfplots.dto.dataset.UpdateDatasetRequest;
import com.pg.pgfplots.entity.DataFile;
import com.pg.pgfplots.mapper.DataFileMapper;
import com.pg.pgfplots.util.FileStorage;
import com.pg.pgfplots.util.SystemLogWriter;
import com.pg.pgfplots.util.TimeFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据集服务：上传/列表/改名/删除/下载。
 * <p>数据隔离：所有查询/修改均带 user_id 条件。</p>
 */
@Service
@RequiredArgsConstructor
public class DatasetService {

    private static final int DATA_NAME_MAX = 50;
    private static final String[] SIZE_UNITS = {"Bytes", "KB", "MB", "GB", "TB"};

    private final DataFileMapper dataFileMapper;
    private final FileStorage fileStorage;
    private final SystemLogWriter systemLogWriter;

    /** 数据集列表（可选关键字过滤），按 update_time 倒序。 */
    public List<DatasetVO> list(Integer userId, String keyword) {
        var query = Wrappers.<DataFile>lambdaQuery().eq(DataFile::getUserId, userId);
        if (keyword != null && !keyword.isEmpty()) {
            query.and(w -> w.like(DataFile::getDataName, keyword).or().like(DataFile::getDescription, keyword));
        }
        query.orderByDesc(DataFile::getUpdateTime);
        List<DataFile> rows = dataFileMapper.selectList(query);
        List<DatasetVO> result = new ArrayList<>(rows.size());
        for (DataFile row : rows) {
            result.add(toVO(row));
        }
        return result;
    }

    /** 上传新数据集。 */
    public DatasetVO upload(Integer userId, String name, String description, MultipartFile file) {
        if (isBlank(name) || isBlank(description)) {
            throw BusinessException.badRequest("数据集名称和描述不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择要上传的文件");
        }

        String original = file.getOriginalFilename();
        if (original == null || original.isEmpty()) {
            original = "upload";
        }
        original = Paths.get(original).getFileName().toString();

        String uniqueName = fileStorage.newFilename(original);
        Path target = fileStorage.getUploadsDir().resolve(uniqueName);
        try {
            Files.createDirectories(fileStorage.getUploadsDir());
            file.transferTo(target);
        } catch (IOException e) {
            systemLogWriter.error("[DATASET] 上传失败: " + e.getMessage());
            throw BusinessException.serverError("上传失败: " + e.getMessage());
        }

        DataFile entity = new DataFile();
        entity.setUserId(userId);
        entity.setDataName(truncateName(name));
        entity.setDataSize((int) file.getSize());
        entity.setDescription(description);
        entity.setFileName(original);
        entity.setFilePath(fileStorage.storedPath(uniqueName));
        entity.setMimetype(file.getContentType());
        try {
            dataFileMapper.insert(entity);
        } catch (Exception e) {
            deleteQuietly(target);
            systemLogWriter.error("[DATASET] 上传失败: " + e.getMessage());
            throw BusinessException.serverError("上传失败: " + e.getMessage());
        }

        DataFile saved = dataFileMapper.selectById(entity.getDataId());
        return toVO(saved);
    }

    /** 更新数据集名称/描述。 */
    public DatasetVO update(Integer userId, Integer id, UpdateDatasetRequest request) {
        String name = request.getName();
        String description = request.getDescription();
        boolean hasName = !isBlank(name);
        boolean hasDescription = description != null;

        if (!hasName && !hasDescription) {
            throw BusinessException.badRequest("请提供数据集名称或描述进行更新");
        }

        var wrapper = Wrappers.<DataFile>lambdaUpdate()
                .eq(DataFile::getDataId, id)
                .eq(DataFile::getUserId, userId);
        if (hasName) {
            if (name.trim().isEmpty()) {
                throw BusinessException.badRequest("数据集名称不能为空");
            }
            wrapper.set(DataFile::getDataName, truncateName(name));
        }
        if (hasDescription) {
            wrapper.set(DataFile::getDescription, description.isEmpty() ? null : description);
        }

        int affected = dataFileMapper.update(null, wrapper);
        if (affected == 0) {
            throw BusinessException.notFound("数据集不存在或无权修改");
        }
        return toVO(dataFileMapper.selectById(id));
    }

    /** 删除数据集及其物理文件。 */
    public void delete(Integer userId, Integer id) {
        DataFile dataFile = dataFileMapper.selectOne(Wrappers.<DataFile>lambdaQuery()
                .eq(DataFile::getDataId, id)
                .eq(DataFile::getUserId, userId));
        if (dataFile == null) {
            throw BusinessException.notFound("数据集不存在");
        }

        dataFileMapper.delete(Wrappers.<DataFile>lambdaQuery()
                .eq(DataFile::getDataId, id)
                .eq(DataFile::getUserId, userId));

        Path path = fileStorage.resolveStored(dataFile.getFilePath());
        deleteQuietly(path);
        if (path != null) {
            systemLogWriter.info("已删除文件: " + path);
        }
    }

    /** 下载：返回物理文件与原始文件名。 */
    public DownloadFile download(Integer userId, Integer id) {
        DataFile dataFile = dataFileMapper.selectOne(Wrappers.<DataFile>lambdaQuery()
                .eq(DataFile::getDataId, id)
                .eq(DataFile::getUserId, userId));
        if (dataFile == null) {
            throw BusinessException.notFound("文件不存在");
        }
        Path path = fileStorage.resolveStored(dataFile.getFilePath());
        if (path == null || !Files.exists(path)) {
            throw BusinessException.notFound("文件不存在");
        }
        return new DownloadFile(path, dataFile.getFileName());
    }

    /** 下载文件承载对象。 */
    public record DownloadFile(Path path, String fileName) {
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            systemLogWriter.warning("[DATASET] 删除物理文件失败: " + e.getMessage());
        }
    }

    private DatasetVO toVO(DataFile row) {
        DatasetVO vo = new DatasetVO();
        vo.setDataId(row.getDataId());
        vo.setUserId(row.getUserId());
        vo.setDataName(row.getDataName());
        vo.setDataSize(row.getDataSize());
        vo.setDescription(row.getDescription());
        vo.setUploadTime(TimeFormat.toMinute(row.getLoadTime()));
        vo.setUpdateTime(TimeFormat.toSecond(row.getUpdateTime()));
        vo.setFileName(row.getFileName());
        vo.setFilePath(row.getFilePath());
        vo.setMimetype(row.getMimetype());
        vo.setSize(formatFileSize(row.getDataSize()));
        vo.setCount(0);
        return vo;
    }

    private String formatFileSize(Integer bytesObj) {
        long bytes = bytesObj == null ? 0 : bytesObj;
        if (bytes == 0) {
            return "0 Bytes";
        }
        int k = 1024;
        int i = (int) Math.floor(Math.log(bytes) / Math.log(k));
        if (i >= SIZE_UNITS.length) {
            i = SIZE_UNITS.length - 1;
        }
        double value = bytes / Math.pow(k, i);
        return (Math.round(value * 100.0) / 100.0) + " " + SIZE_UNITS[i];
    }

    private String truncateName(String value) {
        if (value == null) {
            return "";
        }
        String s = value.trim();
        if (s.codePointCount(0, s.length()) > DATA_NAME_MAX) {
            return s.substring(0, s.offsetByCodePoints(0, DATA_NAME_MAX));
        }
        return s;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
