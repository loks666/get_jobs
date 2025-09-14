package getjobs.service;

import java.util.Map;

/**
 * 数据备份服务接口
 * 提供H2内存数据库的备份和恢复功能
 * 
 * @author getjobs
 * @since v2.0.1
 */
public interface DataBackupService {

    /**
     * 导出当前H2内存数据库中的所有数据到用户目录
     * 
     * @return 备份文件路径
     * @throws Exception 导出过程中的异常
     */
    String exportData() throws Exception;

    /**
     * 从用户目录导入数据到H2内存数据库
     * 
     * @return 是否成功恢复数据
     * @throws Exception 导入过程中的异常
     */
    boolean importData() throws Exception;

    /**
     * 获取备份文件信息
     * 
     * @return 备份文件信息Map
     * @throws Exception 获取信息过程中的异常
     */
    Map<String, Object> getBackupInfo() throws Exception;

    /**
     * 清理备份文件
     * 
     * @return 是否成功删除备份文件
     * @throws Exception 清理过程中的异常
     */
    boolean cleanBackup() throws Exception;
}
