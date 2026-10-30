package com.ihomy.service.schedule;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日程只读来源扩展点:贷款还款日、理财到期、食材到期等「别处已存在、日程页只看不改」的数据,
 * 每类实现一个本接口,{@code ScheduleService} 汇总时遍历实现列表,新增来源不改聚合主逻辑。
 * <p>
 * 实现约定:
 * <ul>
 *   <li>{@link #available(Long)} 判登录/权限,false 时整段跳过、不打 DB(游客与无权限成员不产生空查询);</li>
 *   <li>事项一律用 {@link #readOnlyItem} 生成:带 {@code source}/{@code readOnly}/{@code link},日程页不可改删;</li>
 *   <li>只读,不得回写任何业务表;日期口径复用各自域的唯一算法(贷款首期递推、理财到期日、食材保质期)。</li>
 * </ul>
 */
public interface ScheduleSourceProvider {

    /** 来源标识(同时作为事项的 kind 与 source 字段):LOAN / FINANCE / ITEM_EXPIRY */
    String source();

    /** 当前请求下该来源是否可取(登录态/权限);false 时不查库、不出项 */
    boolean available(Long familyId);

    /** 把区间内(含首尾)的发生日摊平进 items,每项必须带 date */
    void append(Long familyId, LocalDate from, LocalDate to, List<Map<String, Object>> items);

    /** 只读事项的统一形状:kind=source、全天、带跳转目标;子类可再补 period 等展示字段 */
    static Map<String, Object> readOnlyItem(String source, Long id, String title, LocalDate date, String link) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", source);
        item.put("source", source);
        item.put("id", id);
        item.put("title", title);
        item.put("date", date.toString());
        item.put("time", null);
        item.put("readOnly", true);
        item.put("link", link);
        return item;
    }
}
