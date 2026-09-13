package com.pg.pgfplots.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用自定义配置（对应 application.yml 的 app.* 节点）。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Storage storage = new Storage();
    private Latex latex = new Latex();
    private Llm llm = new Llm();
    private Mail mail = new Mail();
    private Rag rag = new Rag();
    private Embedding embedding = new Embedding();

    @Data
    public static class Jwt {
        /** JWT 密钥，缺失即启动失败 */
        private String secret;
        /** 普通用户 token 有效期（小时） */
        private int userExpireHours = 24;
        /** 管理员 token 有效期（天） */
        private int adminExpireDays = 7;
    }

    @Data
    public static class Storage {
        /** 数据集上传目录 */
        private String uploads = "../data/uploads";
        /** 生成历史 JSON 目录 */
        private String history = "../data/storage/history";
        /** 编译产物 PDF 目录 */
        private String charts = "../data/storage/generated_charts";
        /** 编译失败调试目录 */
        private String debug = "../data/storage/debug";
        /** generation_path 相对路径解析基准目录（项目根 hello/） */
        private String relativeBase = "..";
    }

    @Data
    public static class Latex {
        /** xelatex 可执行文件 */
        private String executable = "xelatex";
        /** 编译超时（毫秒） */
        private long timeoutMs = 30000L;
        /** 图表代码最大长度（字符） */
        private int maxCodeLength = 50000;
    }

    @Data
    public static class Llm {
        /** 模型调用超时（毫秒） */
        private long timeoutMs = 45000L;
        /** 最大生成 token */
        private int maxTokens = 8192;
        private Provider deepseek = new Provider();
        private Provider qwen = new Provider();
    }

    @Data
    public static class Provider {
        private String apiKey;
        private String apiUrl;
        private String model;
    }

    @Data
    public static class Mail {
        /** 发件人（与 Node SMTP_FROM 一致） */
        private String from;
        /** 验证码有效期（分钟） */
        private int codeExpireMinutes = 10;
    }

    /** RAG 检索增强配置（批次1/A1）：全链路可关，关闭时行为与旧版一致 */
    @Data
    public static class Rag {
        /** 总开关：false 时 RagService 所有方法直接短路返回，零开销 */
        private boolean enabled = false;
        /** 历史案例召回条数 */
        private int topKHistory = 3;
        /** 模板召回条数 */
        private int topKTemplate = 2;
        /** 余弦相似度阈值，低于该值丢弃 */
        private double minScore = 0.72;
        /** embedding 单次调用超时（毫秒） */
        private long timeoutMs = 3000L;
        /** 检索时最多加载的历史向量条数（防全表膨胀） */
        private int historyLimit = 500;
        /** 检索时最多加载的模板向量条数 */
        private int templateLimit = 200;
    }

    /** OpenAI 兼容 /v1/embeddings 配置（如 SiliconFlow BAAI/bge-m3 或阿里百炼 text-embedding-v4） */
    @Data
    public static class Embedding {
        private String apiKey;
        private String apiUrl = "https://api.siliconflow.cn/v1";
        private String model = "BAAI/bge-m3";
        /** 向量维度，用于写入与校验一致性（bge-m3 / text-embedding-v4 均为 1024） */
        private int dim = 1024;
    }
}
