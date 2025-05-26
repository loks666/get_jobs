package utils;

import java.io.*;
import java.net.URL;

/**
 * 配置文件工具类，用于统一处理配置文件的读写
 */
public class ConfigFileUtil {
    
    /**
     * 获取配置文件的输入流（用于读取）
     * 使用类加载器从编译后的路径读取
     */
    public static InputStream getConfigInputStream() throws IOException {
        InputStream is = ConfigFileUtil.class.getClassLoader().getResourceAsStream("config.yaml");
        if (is == null) {
            throw new FileNotFoundException("无法在类路径中找到 config.yaml 文件");
        }
        return is;
    }
    
    /**
     * 获取配置文件的写入路径
     * 返回源代码目录中的配置文件路径，用于保存修改
     */
    public static String getConfigWritePath() {
        // 始终返回源代码目录的路径，这样保存后下次编译会自动复制到target目录
        return ProjectRootResolver.rootPath + File.separator + "src" + 
               File.separator + "main" + File.separator + "resources" + 
               File.separator + "config.yaml";
    }
    
    /**
     * 获取配置文件的实际路径（兼容旧代码）
     * 优先尝试获取编译后的路径，如果失败则返回源码路径
     */
    public static String getConfigFilePath() {
        // 尝试从类路径获取配置文件
        URL resource = ConfigFileUtil.class.getClassLoader().getResource("config.yaml");
        
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
        
        // 如果类路径中没有找到，返回源码目录的路径
        return getConfigWritePath();
    }
    
    /**
     * 将配置文件从源代码目录复制到编译后的目录
     * 使保存的配置立即生效
     */
    public static void syncConfigToClasspath() throws IOException {
        // 获取编译后的配置文件路径
        URL resource = ConfigFileUtil.class.getClassLoader().getResource("config.yaml");
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
} 