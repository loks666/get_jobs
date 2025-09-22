package getjobs.repository;

import getjobs.repository.entity.ConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigRepository extends JpaRepository<ConfigEntity, Long> {

    /**
     * 获取第一个配置记录（通常只有一个配置）
     */
    @Query("SELECT c FROM ConfigEntity c ORDER BY c.id ASC LIMIT 1")
    Optional<ConfigEntity> findFirstConfig();

    /**
     * 获取默认配置（按创建时间排序的第一个）
     */
    default Optional<ConfigEntity> getDefaultConfig() {
        return findFirstConfig();
    }

    /**
     * 按平台类型获取配置
     */
    Optional<ConfigEntity> findFirstByPlatformTypeOrderByIdAsc(String platformType);
}
