package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.mapper.CookieMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cookie服务类
 */
@Service
@RequiredArgsConstructor
public class CookieService {

    private final CookieMapper cookieMapper;

    /**
     * 根据平台获取Cookie
     * @param platform 平台名称（boss/zhilian/job51/liepin）
     * @return Cookie实体
     */
    public CookieEntity getCookieByPlatform(String platform) {
        LambdaQueryWrapper<CookieEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CookieEntity::getPlatform, platform)
                .orderByDesc(CookieEntity::getUpdatedAt)
                .last("LIMIT 1");
        return cookieMapper.selectOne(wrapper);
    }

    /**
     * 保存或更新Cookie
     * @param platform 平台名称
     * @param cookieValue Cookie值
     * @param remark 备注
     * @return 是否成功
     */
    public boolean saveOrUpdateCookie(String platform, String cookieValue, String remark) {
        CookieEntity existingCookie = getCookieByPlatform(platform);

        if (existingCookie != null) {
            // 更新现有Cookie
            existingCookie.setCookieValue(cookieValue);
            existingCookie.setRemark(remark);
            existingCookie.setUpdatedAt(LocalDateTime.now());
            return cookieMapper.updateById(existingCookie) > 0;
        } else {
            // 新建Cookie
            CookieEntity newCookie = new CookieEntity();
            newCookie.setPlatform(platform);
            newCookie.setCookieValue(cookieValue);
            newCookie.setRemark(remark);
            newCookie.setCreatedAt(LocalDateTime.now());
            newCookie.setUpdatedAt(LocalDateTime.now());
            return cookieMapper.insert(newCookie) > 0;
        }
    }

    /**
     * 清空指定平台的所有Cookie值（处理重复记录场景）
     * @param platform 平台名称
     * @param remark 备注
     * @return 影响行数是否大于0
     */
    public boolean clearCookieByPlatform(String platform, String remark) {
        UpdateWrapper<CookieEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("platform", platform)
                .set("cookie_value", "")
                .set("remark", remark)
                .set("updated_at", LocalDateTime.now());
        return cookieMapper.update(null, updateWrapper) > 0;
    }

    /**
     * 删除指定平台的Cookie
     * @param platform 平台名称
     * @return 是否成功
     */
    public boolean deleteCookie(String platform) {
        LambdaQueryWrapper<CookieEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CookieEntity::getPlatform, platform);
        return cookieMapper.delete(wrapper) > 0;
    }

    /**
     * 获取所有Cookie
     * @return Cookie列表
     */
    public List<CookieEntity> getAllCookies() {
        return cookieMapper.selectList(null);
    }
}
