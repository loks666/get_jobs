package com.getjobs.application.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 管理 Boss 图片简历。文件保存在程序启动目录之外，不写入 classpath 或 JAR。
 */
@Service
public class BossResumeImageService {

    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    public static final String PREVIEW_URL = "/api/boss/config/resume-image/content";
    public static final String RULES_TABLE = "boss_resume_rule";

    private static final Logger log = LoggerFactory.getLogger(BossResumeImageService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> SUPPORTED_EXTENSIONS = List.of("png", "jpg", "gif");
    private final Path storageDirectory = Paths.get(System.getProperty("user.dir"), "data", "boss", "resume")
            .toAbsolutePath()
            .normalize();

    private final JdbcTemplate jdbcTemplate;

    public BossResumeImageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 关键词 → 专属简历 的映射表。建表失败不阻断启动，运行时会回落到默认简历。 */
    @PostConstruct
    public synchronized void ensureRulesTable() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + RULES_TABLE + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "keyword TEXT NOT NULL UNIQUE,"
                    + "image_file TEXT NOT NULL,"
                    + "created_at DATETIME,"
                    + "updated_at DATETIME)");
        } catch (Exception e) {
            log.warn("简历规则表 {} 创建失败，将只使用默认简历：{}", RULES_TABLE, e.getMessage());
        }
    }

    public synchronized ResumeImageInfo save(MultipartFile file) throws IOException {
        validateUpload(file);

        Files.createDirectories(storageDirectory);
        Path temporaryFile = Files.createTempFile(storageDirectory, "resume-upload-", ".tmp");
        try {
            file.transferTo(temporaryFile);
            if (Files.size(temporaryFile) > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("图片大小不能超过 10MB");
            }

            String extension = detectImageExtension(temporaryFile);
            Path target = storageDirectory.resolve("resume." + extension);
            moveReplacing(temporaryFile, target);
            deleteOtherResumeImages(target);
            return toInfo(target);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    public synchronized Optional<ResumeImageInfo> getCurrentImage() {
        return findCurrentImagePath().map(path -> {
            try {
                return toInfo(path);
            } catch (IOException e) {
                return null;
            }
        });
    }

    public synchronized Optional<Path> getCurrentImagePath() {
        return findCurrentImagePath();
    }

    /** 上传某个关键词专属的图片简历，同一关键词重复上传会覆盖。 */
    public synchronized ResumeImageInfo saveForKeyword(String keyword, MultipartFile file) throws IOException {
        String normalized = normalizeKeyword(keyword);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请先填写要绑定的搜索关键词");
        }
        validateUpload(file);

        Files.createDirectories(storageDirectory);
        Path temporaryFile = Files.createTempFile(storageDirectory, "resume-rule-", ".tmp");
        try {
            file.transferTo(temporaryFile);
            if (Files.size(temporaryFile) > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("图片大小不能超过 10MB");
            }
            String extension = detectImageExtension(temporaryFile);
            long id = upsertRuleRow(normalized);
            Path target = storageDirectory.resolve(ruleFileName(id, extension));
            moveReplacing(temporaryFile, target);
            deleteOtherRuleImages(id, target);
            jdbcTemplate.update(
                    "UPDATE " + RULES_TABLE + " SET image_file = ?, updated_at = ? WHERE id = ?",
                    target.getFileName().toString(), timestamp(), id);
            log.info("已为关键词「{}」绑定简历：{}", normalized, target.getFileName());
            return toInfo(target);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /** 列出所有关键词简历规则，附带文件是否存在与预览地址。 */
    public List<Map<String, Object>> listRules() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, keyword, image_file, updated_at FROM " + RULES_TABLE + " ORDER BY keyword");
        } catch (Exception e) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            Object imageFile = row.get("image_file");
            Path path = imageFile == null ? null : storageDirectory.resolve(String.valueOf(imageFile));
            boolean exists = path != null && Files.isRegularFile(path) && Files.isReadable(path);
            item.put("exists", exists);
            if (exists) {
                try {
                    item.put("fileName", path.getFileName().toString());
                    item.put("size", Files.size(path));
                } catch (IOException ignored) {
                    // 读不到大小不影响列表展示
                }
            }
            item.put("previewUrl", "/api/boss/config/resume-rules/" + row.get("id") + "/content");
            result.add(item);
        }
        return result;
    }

    public synchronized boolean deleteRule(long id) {
        String imageFile;
        try {
            imageFile = jdbcTemplate.queryForObject(
                    "SELECT image_file FROM " + RULES_TABLE + " WHERE id = ?", String.class, id);
        } catch (Exception e) {
            return false;
        }
        int affected = jdbcTemplate.update("DELETE FROM " + RULES_TABLE + " WHERE id = ?", id);
        if (imageFile != null && !imageFile.isBlank()) {
            try {
                Files.deleteIfExists(storageDirectory.resolve(imageFile));
            } catch (IOException ignored) {
                // 文件已丢失也不影响规则删除
            }
        }
        return affected > 0;
    }

    /**
     * 投递时按关键词选简历：命中规则就用专属图，否则回落到默认简历 resume.*。
     */
    public Optional<Path> resolveImagePath(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (!normalized.isEmpty()) {
            Optional<Path> bound = findRuleImagePath(normalized);
            if (bound.isPresent()) {
                return bound;
            }
        }
        return findCurrentImagePath();
    }

    /** 判断某个关键词当前会用到哪份简历，仅用于日志与界面提示。 */
    public String describeResolution(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (!normalized.isEmpty() && findRuleImagePath(normalized).isPresent()) {
            return "关键词专属简历";
        }
        return findCurrentImagePath().isPresent() ? "默认简历" : "无简历";
    }

    public synchronized Optional<Path> getRuleImagePath(long id) {
        String imageFile;
        try {
            imageFile = jdbcTemplate.queryForObject(
                    "SELECT image_file FROM " + RULES_TABLE + " WHERE id = ?", String.class, id);
        } catch (Exception e) {
            return Optional.empty();
        }
        if (imageFile == null || imageFile.isBlank()) {
            return Optional.empty();
        }
        Path candidate = storageDirectory.resolve(imageFile);
        return Files.isRegularFile(candidate) && Files.isReadable(candidate)
                ? Optional.of(candidate)
                : Optional.empty();
    }

    private Optional<Path> findRuleImagePath(String keyword) {
        String imageFile;
        try {
            imageFile = jdbcTemplate.queryForObject(
                    "SELECT image_file FROM " + RULES_TABLE + " WHERE keyword = ?", String.class, keyword);
        } catch (Exception e) {
            return Optional.empty();
        }
        if (imageFile == null || imageFile.isBlank()) {
            return Optional.empty();
        }
        Path candidate = storageDirectory.resolve(imageFile);
        return Files.isRegularFile(candidate) && Files.isReadable(candidate)
                ? Optional.of(candidate)
                : Optional.empty();
    }

    private long upsertRuleRow(String keyword) {
        Long existing = null;
        try {
            existing = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + RULES_TABLE + " WHERE keyword = ?", Long.class, keyword);
        } catch (Exception ignored) {
            // 没有记录，走插入分支
        }
        if (existing != null) {
            return existing;
        }
        jdbcTemplate.update(
                "INSERT INTO " + RULES_TABLE + " (keyword, image_file, created_at, updated_at) VALUES (?,?,?,?)",
                keyword, "", timestamp(), timestamp());
        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        return id == null ? 0L : id;
    }

    private static String ruleFileName(long id, String extension) {
        return "kw-" + id + "." + extension;
    }

    private void deleteOtherRuleImages(long id, Path retained) throws IOException {
        for (String extension : SUPPORTED_EXTENSIONS) {
            Path candidate = storageDirectory.resolve(ruleFileName(id, extension));
            if (!candidate.equals(retained)) {
                Files.deleteIfExists(candidate);
            }
        }
    }

    private static String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private static String timestamp() {
        return LocalDateTime.now().format(TS);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片简历");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过 10MB");
        }
    }

    public Map<String, Object> getMetadata() {
        return getCurrentImage()
                .map(info -> Map.<String, Object>of(
                        "exists", true,
                        "fileName", info.fileName(),
                        "contentType", info.contentType(),
                        "size", info.size(),
                        "updatedAt", info.updatedAt(),
                        "previewUrl", PREVIEW_URL
                ))
                .orElseGet(() -> Map.of("exists", false));
    }

    private Optional<Path> findCurrentImagePath() {
        for (String extension : SUPPORTED_EXTENSIONS) {
            Path candidate = storageDirectory.resolve("resume." + extension);
            if (Files.isRegularFile(candidate) && Files.isReadable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private String detectImageExtension(Path image) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(image.toFile())) {
            if (input == null) {
                throw new IllegalArgumentException("无法读取图片文件");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("文件不是有效的 PNG、JPG 或 GIF 图片");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                // 读取尺寸会让 ImageIO 实际解析图片头，避免只相信文件名或 Content-Type。
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new IllegalArgumentException("图片尺寸无效");
                }
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                return switch (format) {
                    case "png" -> "png";
                    case "jpeg", "jpg" -> "jpg";
                    case "gif" -> "gif";
                    default -> throw new IllegalArgumentException("仅支持 PNG、JPG、JPEG 和 GIF 图片");
                };
            } finally {
                reader.dispose();
            }
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteOtherResumeImages(Path retained) throws IOException {
        for (String extension : SUPPORTED_EXTENSIONS) {
            Path candidate = storageDirectory.resolve("resume." + extension);
            if (!candidate.equals(retained)) {
                Files.deleteIfExists(candidate);
            }
        }
    }

    private ResumeImageInfo toInfo(Path path) throws IOException {
        String fileName = path.getFileName().toString();
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String contentType = switch (extension) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> "image/jpeg";
        };
        return new ResumeImageInfo(
                path,
                fileName,
                contentType,
                Files.size(path),
                Files.getLastModifiedTime(path).toMillis()
        );
    }

    public record ResumeImageInfo(Path path, String fileName, String contentType, long size, long updatedAt) {
    }
}
