package com.ihomy.service.assistant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Album;
import com.ihomy.entity.Diary;
import com.ihomy.entity.Item;
import com.ihomy.entity.Wish;
import com.ihomy.mapper.AlbumMapper;
import com.ihomy.mapper.DiaryMapper;
import com.ihomy.mapper.ItemMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.WishMapper;
import com.ihomy.service.BlogService;
import com.ihomy.service.access.ContentAccessService;
import com.ihomy.service.access.ContentKind;
import com.ihomy.service.access.ViewerScope;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class AssistantSearchService {
    private final BlogService blogs;
    private final DiaryMapper diaries;
    private final ItemMapper items;
    private final WishMapper wishes;
    private final AlbumMapper albums;
    private final SysUserMapper users;
    private final ContentAccessService access;

    List<Map<String, Object>> members(Long familyId) {
        return users.selectMembersByFamily(familyId).stream().map(m -> Map.<String, Object>of(
                "id", m.get("id"), "name", m.getOrDefault("nickname", m.getOrDefault("username", "家庭成员")),
                "role", m.get("roleCode"))).toList();
    }

    Map<String, Object> recipient(Long familyId, long id) {
        return members(familyId).stream().filter(m -> ((Number) m.get("id")).longValue() == id)
                .findFirst().orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "找不到这位家庭成员"));
    }

    List<Map<String, Object>> search(ViewerScope viewer, Map<String, Object> args) {
        String kind = (String) args.getOrDefault("kind", "all");
        String keyword = (String) args.getOrDefault("keyword", "");
        int limit = ((Number) args.getOrDefault("limit", 10)).intValue();
        boolean hasDiaryFilter = args.containsKey("from") || args.containsKey("to") || Boolean.TRUE.equals(args.get("mine"));
        if (hasDiaryFilter && !"diary".equals(kind)) throw AssistantToolSpec.invalid();
        if (keyword.isEmpty() && !hasDiaryFilter) throw AssistantToolSpec.invalid();
        List<Map<String, Object>> matches = new ArrayList<>();
        if (kind.equals("all") || kind.equals("blog")) {
            blogs.searchFamilyTitles(viewer, keyword, limit).forEach(b ->
                    matches.add(hit("blog", b.getId(), b.getTitle(), "/blog/" + b.getId())));
        }
        if (kind.equals("all") || kind.equals("diary")) {
            LambdaQueryWrapper<Diary> query = new LambdaQueryWrapper<>();
            access.applyVisible(query, ContentKind.DIARY, viewer, Diary::getFamilyId, Diary::getAuthorId, Diary::getVisibility);
            LocalDate from = AssistantTools.date(args, "from");
            LocalDate to = AssistantTools.date(args, "to");
            if (from != null && to != null && from.isAfter(to)) throw AssistantToolSpec.invalid();
            query.select(Diary::getId, Diary::getAuthorId, Diary::getContent, Diary::getCreatedAt)
                    .like(!keyword.isEmpty(), Diary::getContent, keyword)
                    .eq(Boolean.TRUE.equals(args.get("mine")), Diary::getAuthorId, viewer.userId())
                    .ge(from != null, Diary::getCreatedAt, from == null ? null : from.atStartOfDay())
                    .lt(to != null, Diary::getCreatedAt, to == null ? null : to.plusDays(1).atStartOfDay())
                    .orderByDesc(Diary::getCreatedAt).last("LIMIT " + limit);
            diaries.selectList(query).forEach(d -> {
                String body = d.getContent() == null ? "" : d.getContent().strip();
                String title = d.getCreatedAt().toLocalDate() + " " + body.substring(0, Math.min(80, body.length()));
                matches.add(hit("diary", d.getId(), title, "/diary/book/" + d.getAuthorId() + "?entry=" + d.getId()));
            });
        }
        if (kind.equals("all") || kind.equals("album")) {
            LambdaQueryWrapper<Album> query = new LambdaQueryWrapper<>();
            access.applyVisible(query, ContentKind.ALBUM, viewer, Album::getFamilyId, Album::getCreatedBy, Album::getVisibility);
            albums.selectList(query.select(Album::getName, Album::getId).like(Album::getName, keyword)
                    .orderByDesc(Album::getId).last("LIMIT " + limit)).forEach(a ->
                    matches.add(hit("album", a.getId(), a.getName(), "/album/" + a.getId())));
        }
        if (kind.equals("all") || kind.equals("wish")) {
            LambdaQueryWrapper<Wish> query = new LambdaQueryWrapper<>();
            access.applyVisible(query, ContentKind.WISH, viewer, Wish::getFamilyId, Wish::getRequesterId, Wish::getVisibility);
            wishes.selectList(query.select(Wish::getTitle, Wish::getId).like(Wish::getTitle, keyword)
                    .orderByAsc(Wish::getStatus).orderByDesc(Wish::getCreatedAt).last("LIMIT " + limit)).forEach(w ->
                    matches.add(hit("wish", w.getId(), w.getTitle(), "/wish")));
        }
        if (kind.equals("all") || kind.equals("item")) {
            LambdaQueryWrapper<Item> query = new LambdaQueryWrapper<>();
            access.applyVisible(query, ContentKind.ITEM, viewer, Item::getFamilyId, Item::getCreatedBy, Item::getVisibility);
            items.selectList(query.select(Item::getName, Item::getId)
                    .and(q -> q.like(Item::getName, keyword).or().like(Item::getAliases, keyword))
                    .orderByAsc(Item::getType, Item::getId).last("LIMIT " + limit)).forEach(i ->
                    matches.add(hit("item", i.getId(), i.getName(), "/item")));
        }
        if (kind.equals("all") || kind.equals("member")) {
            members(viewer.familyId()).stream().filter(m -> m.get("name").toString().toLowerCase(Locale.ROOT)
                    .contains(keyword.toLowerCase(Locale.ROOT))).forEach(m ->
                    matches.add(hit("member", (Number) m.get("id"), (String) m.get("name"), "/member")));
        }
        return matches.stream().limit(limit).toList();
    }

    private static Map<String, Object> hit(String kind, Number id, String title, String path) {
        return Map.of("kind", kind, "id", id, "title", title, "path", path);
    }
}
