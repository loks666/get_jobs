package utils;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 配置文件工具类，用于统一处理配置文件的读写
 */
public class ConfigFileUtil {
    
    // 文件路径常量
    private static final String CONFIG_FILE_NAME = "config.yaml";
    private static final String DATA_FILE_NAME = "data.json";
    private static final String COOKIE_FILE_NAME = "cookie.json";
    
    /**
     * 获取配置文件的输入流（用于读取）
     * 优先从工作目录的data目录读取，否则使用类加载器从编译后的路径读取
     */
    public static InputStream getConfigInputStream() throws IOException {
        // 优先从工作目录的data目录读取配置文件
        String configPath = getConfigFilePath();
        File configFile = new File(configPath);
        
        if (configFile.exists()) {
            return new FileInputStream(configFile);
        }
        
        // 如果data目录的配置文件不存在，回退到classpath
        InputStream is = ConfigFileUtil.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME);
        if (is == null) {
            throw new FileNotFoundException("无法找到 " + CONFIG_FILE_NAME + " 文件，请检查data目录或classpath");
        }
        return is;
    }
    
    /**
     * 获取配置文件的写入路径
     * 返回工作目录下data目录中的配置文件路径，用于保存修改
     */
    public static String getConfigWritePath() {
        // 返回工作目录下data目录的路径，确保配置文件保存到用户数据目录
        return System.getProperty("user.dir") + File.separator + "data" +
               File.separator + CONFIG_FILE_NAME;
    }
    
    /**
     * 获取配置文件的实际路径（兼容旧代码）
     * 优先尝试获取编译后的路径，如果失败则返回data目录路径
     */
    public static String getConfigFilePath() {
        // 尝试从类路径获取配置文件
        URL resource = ConfigFileUtil.class.getClassLoader().getResource(CONFIG_FILE_NAME);
        
        if (resource != null) {
            // 如果在类路径中找到了配置文件，返回其实际路径
            try {
                return new File(resource.toURI()).getAbsolutePath();
            } catch (Exception e) {
                // 如果转换失败，使用 URL 的路径
                String path = resource.getPath();
                // Windows 系统下需要处理路径前缀
                if (path.startsWith("/") && path.matches("^/[A-Za-z]:.*")) {
                    path = path.substring(1);
                }
                return path.replace("/", File.separator);
            }
        }
        
        // 如果类路径中没有找到，返回data目录的路径
        return getConfigWritePath();
    }
    
    /**
     * 获取数据文件路径
     * 返回工作目录下data目录中的数据文件路径
     */
    public static String getDataFilePath() {
        return System.getProperty("user.dir") + File.separator + "data" + 
               File.separator + DATA_FILE_NAME;
    }
    
    /**
     * 获取Cookie文件路径
     * 返回工作目录下data目录中的cookie文件路径
     */
    public static String getCookieFilePath() {
        return System.getProperty("user.dir") + File.separator + "data" + 
               File.separator + COOKIE_FILE_NAME;
    }
    
    /**
     * 获取data目录路径
     * 返回工作目录下的data目录路径
     */
    public static String getDataDirectoryPath() {
        return System.getProperty("user.dir") + File.separator + "data";
    }
    
    /**
     * 确保data目录存在
     * 如果目录不存在则创建
     */
    public static void ensureDataDirectoryExists() {
        String dataDir = getDataDirectoryPath();
        File dataDirectory = new File(dataDir);
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
    }
    
    /**
     * 初始化配置文件
     * 如果配置文件不存在，从resources目录复制到data目录
     */
    public static void initializeConfigFile() throws IOException {
        String configPath = getConfigWritePath();
        File configFile = new File(configPath);
        
        if (!configFile.exists()) {
            ensureDataDirectoryExists();
            
            // 从resources目录读取config.yaml
            InputStream configStream = ConfigFileUtil.class.getClassLoader()
                    .getResourceAsStream(CONFIG_FILE_NAME);
            if (configStream != null) {
                // 复制到data目录
                Files.copy(configStream, Paths.get(configPath));
                configStream.close();
            } else {
                throw new FileNotFoundException("无法找到配置文件模板: " + CONFIG_FILE_NAME);
            }
        }
    }
    
    /**
     * 初始化数据文件
     * 如果数据文件不存在，创建初始的JSON结构
     */
    public static void initializeDataFile() throws IOException {
        String dataPath = getDataFilePath();
        File dataFile = new File(dataPath);
        
        if (!dataFile.exists()) {
            ensureDataDirectoryExists();
            
            // 创建文件并写入初始JSON结构
            Map<String, Set<String>> initialData = new HashMap<>();
            initialData.put("blackCompanies", new HashSet<>());
            initialData.put("blackRecruiters", new HashSet<>());
            initialData.put("blackJobs", new HashSet<>());
            String initialJson = customJsonFormat(initialData);
            Files.write(Paths.get(dataPath), initialJson.getBytes());
        }
    }
    
    /**
     * 初始化Cookie文件
     * 如果Cookie文件不存在，创建空的JSON数组
     */
    public static void initializeCookieFile() throws IOException {
        String cookiePath = getCookieFilePath();
        File cookieFile = new File(cookiePath);
        
        if (!cookieFile.exists()) {
            ensureDataDirectoryExists();
            
            // 创建空的cookie文件
            Files.write(Paths.get(cookiePath), "[]".getBytes());
        }
    }
    
    /**
     * 初始化所有必要的文件
     * 包括配置文件、数据文件和Cookie文件
     */
    public static void initializeAllFiles() throws IOException {
        initializeConfigFile();
        initializeDataFile();
        initializeCookieFile();
    }
    
    /**
     * 将配置文件从源代码目录复制到编译后的目录
     * 使保存的配置立即生效
     */
    public static void syncConfigToClasspath() throws IOException {
        // 获取编译后的配置文件路径
        URL resource = ConfigFileUtil.class.getClassLoader().getResource(CONFIG_FILE_NAME);
        if (resource != null) {
            try {
                File targetFile = new File(resource.toURI());
                File sourceFile = new File(getConfigWritePath());
                
                // 复制文件
                try (InputStream in = new FileInputStream(sourceFile);
                     OutputStream out = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                }
            } catch (Exception e) {
                throw new IOException("无法同步配置文件到类路径: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * 自定义JSON格式化方法
     * 用于格式化数据文件的JSON结构
     */
    private static String customJsonFormat(Map<String, Set<String>> data) {
        StringBuilder json = new StringBuilder("{\n");
        boolean first = true;
        
        for (Map.Entry<String, Set<String>> entry : data.entrySet()) {
            if (!first) {
                json.append(",\n");
            }
            json.append("  \"").append(entry.getKey()).append("\": [");
            
            Set<String> set = entry.getValue();
            boolean firstItem = true;
            for (String item : set) {
                if (!firstItem) {
                    json.append(", ");
                }
                json.append("\"").append(item).append("\"");
                firstItem = false;
            }
            
            json.append("]");
            first = false;
        }
        
        json.append("\n}");
        return json.toString();
    }
    
    /**
     * 测试方法：验证所有路径是否正确
     * 用于调试和验证路径管理
     */
    public static void testPaths() {
        System.out.println("=== 路径管理测试 ===");
        System.out.println("配置文件路径: " + getConfigFilePath());
        System.out.println("配置文件写入路径: " + getConfigWritePath());
        System.out.println("数据文件路径: " + getDataFilePath());
        System.out.println("Cookie文件路径: " + getCookieFilePath());
        System.out.println("数据目录路径: " + getDataDirectoryPath());
        System.out.println("==================");
    }
} 