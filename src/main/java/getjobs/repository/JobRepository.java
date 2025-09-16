package getjobs.repository;

import getjobs.repository.entity.JobEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRepository extends JpaRepository<JobEntity, Long> {

    @Query("SELECT j FROM JobEntity j " +
            "WHERE (:platform IS NULL OR LOWER(j.platform) = LOWER(:platform)) " +
            "AND ( :keyword IS NULL " +
            "   OR LOWER(j.jobTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(j.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(j.hrName) LIKE LOWER(CONCAT('%', :keyword, '%')) )")
    Page<JobEntity> search(@Param("platform") String platform,
                           @Param("keyword") String keyword,
                           Pageable pageable);

    /**
     * 根据加密职位ID检查职位是否存在
     *
     * @param encryptJobId 加密职位ID
     * @return 是否存在
     */
    boolean existsByEncryptJobId(String encryptJobId);

    /**
     * 根据平台统计职位数量
     *
     * @param platform 平台名称
     * @return 职位数量
     */
    long countByPlatform(String platform);

    /**
     * 根据安全ID查找职位
     *
     * @param securityId 安全ID
     * @return 职位实体
     */
    JobEntity findBySecurityId(String securityId);

    /**
     * 根据加密职位ID查找职位
     *
     * @param encryptJobId 加密职位ID
     * @return 职位实体
     */
    JobEntity findByEncryptJobId(String encryptJobId);

    /**
     * 查找状态不等于指定值的职位
     *
     * @param status 状态值
     * @return 职位实体列表
     */
    List<JobEntity> findByStatusNot(Integer status);

    List<JobEntity> findAllByEncryptJobIdIn(List<String> encryptJobIds);
}
