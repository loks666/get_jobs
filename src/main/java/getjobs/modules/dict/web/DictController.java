package getjobs.modules.dict.web;

import getjobs.enums.RecruitmentPlatformEnum;
import getjobs.modules.dict.api.DictBundle;
import getjobs.modules.dict.api.DictGroup;
import getjobs.modules.dict.service.DictFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dicts")
public class DictController {

    private final DictFacade dictFacade;

    public DictController(DictFacade dictFacade) {
        this.dictFacade = dictFacade;
    }

    /**
     * 平台字典值
     *
     * @param platform
     * @return
     */
    @GetMapping("/{platform}")
    public DictBundle all(@PathVariable("platform") RecruitmentPlatformEnum platform) {
        return dictFacade.fetchAll(platform);
    }

    // 取某一分组（如 payTypeList）
    @GetMapping("/{platform}/{key}")
    public ResponseEntity<DictGroup> group(@PathVariable("platform") RecruitmentPlatformEnum platform,
                                           @PathVariable("key") String key) {
        return dictFacade.fetchByKey(platform, key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}