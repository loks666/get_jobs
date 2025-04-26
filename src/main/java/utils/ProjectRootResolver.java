package utils;

import java.io.File;

public class ProjectRootResolver {

    public static String rootPath = findProjectRoot().getAbsolutePath();

    public static File findProjectRoot() {
        // 获取当前类加载路径
        String classPath = ProjectRootResolver.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();

        File dir = new File(classPath).getAbsoluteFile();

        // 如果是 /target/classes 或 /build/classes/java/main，跳两层
        if (dir.getPath().contains("/target/")) {
            dir = dir.getParentFile().getParentFile();
        } else if (dir.getPath().contains("/build/")) {
            dir = dir.getParentFile().getParentFile();
        }

        // 向上递归查找标志文件
        while (dir != null) {
            if (new File(dir, "pom.xml").exists() ||
                new File(dir, "build.gradle").exists() ||
                new File(dir, ".git").exists()) {
                return dir;
            }
            dir = dir.getParentFile();
        }

        return null;
    }

}