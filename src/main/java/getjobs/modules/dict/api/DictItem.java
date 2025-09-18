package getjobs.modules.dict.api;

/**
 * 字典项（如 薪资区间 3-5K）
 * lowSalary, highSalary 仅 salaryList 有值
 */
public record DictItem(String code, String name, Integer lowSalary, Integer highSalary) {
    public DictItem(String code, String name) { this(code, name, null, null); }
}