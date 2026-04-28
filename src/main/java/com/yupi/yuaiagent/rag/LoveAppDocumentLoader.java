package com.yupi.yuaiagent.rag;// 导入日志注解，用于打印日志
import lombok.extern.slf4j.Slf4j;
// Spring AI 核心文档对象，所有文件内容都会封装成这个对象
import org.springframework.ai.document.Document;
// Markdown 文件读取器（核心类，专门读取 .md 文件）
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
// Markdown 读取配置类，自定义读取规则
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
// Spring 资源对象，代表一个文件（比如 md 文件、图片、配置文件）
import org.springframework.core.io.Resource;
// Spring 资源模式解析器，用于批量读取文件
import org.springframework.core.io.support.ResourcePatternResolver;
// 声明这是 Spring 组件，让 Spring 自动管理这个类
import org.springframework.stereotype.Component;

// 导入异常处理、集合工具类
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 文档加载器
 * 作用：批量读取项目中 resources/document/ 下的所有 Markdown 文件
 * 适配：Spring AI 1.1.2 正式版
 */
// 1. 核心注解：将这个类交给 Spring 容器管理，成为 Bean，可以被其他类自动注入使用
@Component
// 2. Lombok 注解：自动生成日志对象 log，直接用 log.info()/log.error() 打印日志
@Slf4j
// 3. 定义类，公开访问（Spring 组件推荐 public）
public class LoveAppDocumentLoader {

    // 4. 声明 Spring 资源解析器（固定用法）
    // 作用：批量读取项目资源文件（比如读取所有 .md 结尾的文件）
    private final ResourcePatternResolver resourcePatternResolver;

    // 5. 构造方法注入（Spring 官方推荐的注入方式，比 @Autowired 更安全）
    // Spring 会自动把 ResourcePatternResolver 对象传给这个类
    public LoveAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 6. 核心业务方法
     * 作用：加载所有 Markdown 文件，返回封装好的 Document 集合
     * 返回值：List<Document>  Spring AI 统一的文档列表
     */
    public List<Document> loadMarkdowns() {
        // 7. 创建空集合，用于存储所有读取到的 Markdown 文档
        List<Document> allDocuments = new ArrayList<>();


        try {
            // 8. 核心代码：批量读取文件
            // classpath:document/*.md  →  读取 resources 目录下 document 文件夹里 所有 .md 文件
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");

            // 9. 健壮性判断：如果没有找到任何 md 文件，打印警告日志，直接返回空集合
            if (resources.length == 0) {
                log.warn("未找到任何 Markdown 文件，请检查路径：resources/document/");
                return allDocuments;
            }

            // 10. 遍历所有找到的 md 文件
            for (Resource resource : resources) {
                // 11. 获取当前文件的文件名（比如：help.md、user.md）
                String fileName = resource.getFilename();
                //提取文档倒数第三个字和倒数第二个字作为标签,给每个文档设置状态
                //状态有"单身","已婚","恋爱"三种
                String status=fileName.substring(fileName.length()-6,fileName.length()-4);
                // 12. 构建 Markdown 读取配置（建造者模式，自定义读取规则）
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        // 遇到 Markdown 水平分割线 --- 时，分割为独立文档
                        .withHorizontalRuleCreateDocument(true)
                        // 不读取 Markdown 中的代码块（```java``` 这类内容忽略）
                        .withIncludeCodeBlock(false)
                        // 不读取 Markdown 中的引用块（> 内容 忽略）
                        .withIncludeBlockquote(false)
                        // 给文档添加自定义元数据：记录当前文件的文件名
                        .withAdditionalMetadata("filename", fileName)
                        //给文档添加自定义元数据：记录当前文件所属的状态
                        .withAdditionalMetadata("status", status)
                        // 构建配置对象
                        .build();

                // 13. 创建 Markdown 读取器，传入 要读取的文件 + 读取规则
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);

                // 14. 【核心】读取文件内容，转为 Spring AI 标准 Document 对象
                // 注意：Spring AI 1.0+ 版本必须用 read()，旧版 get() 已废弃
                allDocuments.addAll(reader.read());

                // 15. 打印成功日志
                log.info("成功加载 Markdown 文档：{}", fileName);
            }

        // 16. 异常捕获：如果文件读取失败（比如文件损坏、路径错误），打印错误日志
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }

        // 17. 返回所有读取到的文档
        return allDocuments;
    }
}