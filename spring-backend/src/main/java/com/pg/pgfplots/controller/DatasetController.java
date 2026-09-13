package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.dto.dataset.DatasetVO;
import com.pg.pgfplots.dto.dataset.UpdateDatasetRequest;
import com.pg.pgfplots.security.SecurityUtils;
import com.pg.pgfplots.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据集接口：/api/datasets/*。
 */
@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    /** 数据集列表 */
    @GetMapping
    public Result<List<DatasetVO>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok("获取成功", datasetService.list(SecurityUtils.userId(), keyword));
    }

    /** 上传数据集（multipart：file + name + description） */
    @PostMapping
    public Result<DatasetVO> upload(@RequestParam(value = "file", required = false) MultipartFile file,
                                    @RequestParam(value = "name", required = false) String name,
                                    @RequestParam(value = "description", required = false) String description) {
        return Result.ok("上传成功", datasetService.upload(SecurityUtils.userId(), name, description, file));
    }

    /** 更新数据集 */
    @PostMapping("/{id}/update")
    public Result<DatasetVO> update(@PathVariable("id") Integer id,
                                    @RequestBody UpdateDatasetRequest request) {
        return Result.ok("更新成功", datasetService.update(SecurityUtils.userId(), id, request));
    }

    /** 删除数据集 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        datasetService.delete(SecurityUtils.userId(), id);
        return Result.ok("删除成功", null);
    }

    /** 下载数据集原始文件 */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable("id") Integer id) {
        DatasetService.DownloadFile download = datasetService.download(SecurityUtils.userId(), id);
        FileSystemResource resource = new FileSystemResource(download.path());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
