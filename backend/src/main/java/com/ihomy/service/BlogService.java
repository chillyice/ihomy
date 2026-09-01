package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BlogDTO;
import com.ihomy.entity.Blog;
import com.ihomy.entity.BlogCategory;
import com.ihomy.mapper.BlogCategoryMapper;
import com.ihomy.mapper.BlogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 博客业务:分页查询按家庭/可见范围过滤,增删改校验归属与权限。
 * 可见性:0仅自己/1指定成员/2指定群组/3家庭可见/4公开。
 */
@Service
@RequiredArgsConstructor
public class BlogService {

    /** 未分类:博客不选分类时的归宿(空/清空分类统一落这里,列表计数可见) */
    public static final String CATEGORY_UNCATEGORIZED = "未分类";

    /** 新家庭初始博客分类(创建家庭时注入,家长可改名/删除;未分类固定最后) */
    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "生活随笔", "家庭时光", "旅行游记", "美食记录", "育儿亲子",
            "健康运动", "读书笔记", "兴趣爱好", CATEGORY_UNCATEGORIZED);

    private final BlogMapper blogMapper;
    private final BlogCategoryMapper blogCategoryMapper;
    private final PointsService pointsService;

    /** 为家庭注入初始分类(幂等:已有任意分类的家庭跳过,不干扰用户自建树) */
    public void seedDefaultCategories(Long familyId) {
        LambdaQueryWrapper<BlogCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(BlogCategory::getFamilyId, familyId);
        if (blogCategoryMapper.selectCount(qw) > 0) return;
        int sort = 1;
        for (String name : DEFAULT_CATEGORIES) {
            BlogCategory c = new BlogCategory();
            c.setName(name);
            c.setFamilyId(familyId);
            c.setSortOrder(sort++);
            blogCategoryMapper.insert(c);
        }
    }

    /** 分页查询:OWNER 见全家;成员见自己的+家庭可见/公开;游客仅公开 */
    public IPage<Blog> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner, String keyword, String category) {
        LambdaQueryWrapper<Blog> qw = new LambdaQueryWrapper<>();
        if (familyId != null) {
            qw.eq(Blog::getFamilyId, familyId);
            if (isOwner) {
                // 家长可见全部
            } else if (currentUserId != null) {
                qw.and(w -> w.eq(Blog::getAuthorId, currentUserId)
                            .or().in(Blog::getVisibility, DictConst.VIS_FAMILY, DictConst.VIS_PUBLIC));
            } else {
                qw.eq(Blog::getVisibility, DictConst.VIS_PUBLIC);
            }
        } else {
            qw.eq(Blog::getVisibility, DictConst.VIS_PUBLIC);
        }
        qw.eq(Blog::getStatus, DictConst.BLOG_PUBLISHED)
          .eq(StringUtils.hasText(category), Blog::getCategory, category)
          .like(StringUtils.hasText(keyword), Blog::getTitle, keyword)
          .orderByDesc(Blog::getCreatedAt);
        return blogMapper.selectPage(new Page<>(current, size), qw);
    }

    /** 家庭级分类列表:返回树形结构(含 id/name/parentId/path) */
    public List<Map<String, Object>> categories(Long familyId) {
        LambdaQueryWrapper<BlogCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(BlogCategory::getFamilyId, familyId).orderByAsc(BlogCategory::getSortOrder).orderByAsc(BlogCategory::getName);
        List<BlogCategory> all = blogCategoryMapper.selectList(qw);

        Map<Long, String> pathMap = new HashMap<>();
        Map<Long, List<BlogCategory>> childrenMap = new HashMap<>();
        List<BlogCategory> roots = new ArrayList<>();
        for (BlogCategory c : all) {
            if (c.getParentId() == null) roots.add(c);
            else childrenMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        buildTree(roots, childrenMap, pathMap, "", 0, result);
        return result;
    }

    private void buildTree(List<BlogCategory> nodes, Map<Long, List<BlogCategory>> childrenMap,
                           Map<Long, String> pathMap, String parentPath, int depth, List<Map<String, Object>> result) {
        for (BlogCategory c : nodes) {
            String path = parentPath.isEmpty() ? c.getName() : parentPath + "/" + c.getName();
            pathMap.put(c.getId(), path);
            List<BlogCategory> children = childrenMap.getOrDefault(c.getId(), List.of());
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", c.getId());
            node.put("name", c.getName());
            node.put("parentId", c.getParentId());
            node.put("path", path);
            node.put("depth", depth);
            node.put("childCount", children.size());
            result.add(node);
            buildTree(children, childrenMap, pathMap, path, depth + 1, result);
        }
    }

    /** 分类计数:分类表全量 + 博客表按权限统计,合并后返回(空分类count=0) */
    public List<Map<String, Object>> categoryCounts(Long familyId, Long authorId, boolean isOwner) {
        // 1. 从分类表拿全量树(含path)
        List<Map<String, Object>> tree = categories(familyId);

        // 2. 从博客表按权限统计每个分类(按category字段=全路径)文章数
        List<Map<String, Object>> counts = blogMapper.selectCategoryCounts(familyId, authorId, isOwner);
        Map<String, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : counts) {
            countMap.put((String) row.get("category"), ((Number) row.get("cnt")).longValue());
        }

        // 3. 合并:分类表全量(按path匹配count) + 博客表可能有分类表中不存在的旧分类
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> node : tree) {
            String path = (String) node.get("path");
            Map<String, Object> row = new LinkedHashMap<>(node);
            row.put("cnt", countMap.getOrDefault(path, 0L));
            result.add(row);
            seen.add(path);
        }
        for (Map<String, Object> row : counts) {
            String cat = (String) row.get("category");
            if (!seen.contains(cat)) {
                Map<String, Object> legacy = new LinkedHashMap<>();
                legacy.put("id", null);
                legacy.put("name", cat);
                legacy.put("parentId", null);
                legacy.put("path", cat);
                legacy.put("depth", 0);
                legacy.put("childCount", 0);
                legacy.put("cnt", ((Number) row.get("cnt")).longValue());
                result.add(legacy);
                seen.add(cat);
            }
        }
        return result;
    }

    /** 新增分类:持久化到分类表(parentId=NULL为顶级) */
    public void addCategory(Long familyId, String name, Long parentId) {
        if (!StringUtils.hasText(name)) throw new BizException(ResultCode.BAD_REQUEST);
        // 校验父分类存在
        if (parentId != null) {
            BlogCategory parent = blogCategoryMapper.selectById(parentId);
            if (parent == null || !parent.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        }
        // 校验同父下同名不存在
        LambdaQueryWrapper<BlogCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(BlogCategory::getFamilyId, familyId)
          .eq(BlogCategory::getName, name)
          .eq(parentId != null, BlogCategory::getParentId, parentId)
          .isNull(parentId == null, BlogCategory::getParentId);
        if (blogCategoryMapper.selectCount(qw) > 0) throw new BizException(ResultCode.BAD_REQUEST);
        BlogCategory cat = new BlogCategory();
        cat.setName(name);
        cat.setParentId(parentId);
        cat.setFamilyId(familyId);
        blogCategoryMapper.insert(cat);
    }

    /** 更新分类:可改名+改父级,级联更新博客表全路径(含子分类) */
    public void renameCategory(Long familyId, Long categoryId, String newName, Long newParentId) {
        if (!StringUtils.hasText(newName)) throw new BizException(ResultCode.BAD_REQUEST);
        BlogCategory cat = blogCategoryMapper.selectById(categoryId);
        if (cat == null || !cat.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);

        // 校验新父级存在且不是自己/自己的后代(防环)
        if (newParentId != null) {
            if (newParentId.equals(categoryId)) throw new BizException(ResultCode.BAD_REQUEST);
            BlogCategory parent = blogCategoryMapper.selectById(newParentId);
            if (parent == null || !parent.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
            // 检查新父级不是当前分类的后代
            List<Map<String, Object>> tree = categories(familyId);
            List<Long> descendants = new ArrayList<>();
            collectChildIds(tree, categoryId, descendants);
            if (descendants.contains(newParentId)) throw new BizException(ResultCode.BAD_REQUEST);
        }

        // 计算旧全路径
        List<Map<String, Object>> tree = categories(familyId);
        String oldPath = null;
        for (Map<String, Object> node : tree) {
            if (categoryId.equals(node.get("id"))) { oldPath = (String) node.get("path"); break; }
        }
        if (oldPath == null) throw new BizException(ResultCode.NOT_FOUND);

        // 计算新全路径
        String newParentPath = "";
        if (newParentId != null) {
            for (Map<String, Object> node : tree) {
                if (newParentId.equals(node.get("id"))) { newParentPath = (String) node.get("path"); break; }
            }
        }
        String newPath = newParentPath.isEmpty() ? newName : newParentPath + "/" + newName;

        // 更新分类表
        cat.setName(newName);
        cat.setParentId(newParentId);
        blogCategoryMapper.updateById(cat);

        // 更新博客表:该分类及其子分类的全路径(旧前缀→新前缀)
        renameBlogCategoryPaths(familyId, oldPath, newPath);
        renameChildPaths(familyId, categoryId, oldPath, newPath, tree);
    }

    private void renameChildPaths(Long familyId, Long parentId, String oldPrefix, String newPrefix, List<Map<String, Object>> tree) {
        for (Map<String, Object> node : tree) {
            if (parentId.equals(node.get("parentId"))) {
                String oldChildPath = (String) node.get("path");
                String newChildPath = newPrefix + oldChildPath.substring(oldPrefix.length());
                renameBlogCategoryPaths(familyId, oldChildPath, newChildPath);
                renameChildPaths(familyId, (Long) node.get("id"), oldChildPath, newChildPath, tree);
            }
        }
    }

    private void renameBlogCategoryPaths(Long familyId, String oldPath, String newPath) {
        // 精确匹配
        blogMapper.renameCategory(familyId, oldPath, newPath);
        // 前缀匹配(子分类的博客: oldPath/xxx → newPath/xxx)
        blogMapper.renameCategoryPrefix(familyId, oldPath + "/", newPath + "/");
    }

    /** 删除分类:删分类表记录(含子分类) + 按mode处理博客表 */
    public void deleteCategory(Long familyId, Long categoryId, String mode) {
        BlogCategory cat = blogCategoryMapper.selectById(categoryId);
        if (cat == null || !cat.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);

        // 收集该分类及所有子分类的全路径
        List<Map<String, Object>> tree = categories(familyId);
        List<String> pathsToDelete = new ArrayList<>();
        String targetPath = null;
        collectChildPaths(tree, categoryId, categoryId, null, pathsToDelete);
        for (Map<String, Object> node : tree) {
            if (categoryId.equals(node.get("id"))) { targetPath = (String) node.get("path"); break; }
        }
        if (targetPath != null) pathsToDelete.add(targetPath);

        // 删分类表(本分类+子分类)
        List<Long> idsToDelete = new ArrayList<>();
        idsToDelete.add(categoryId);
        collectChildIds(tree, categoryId, idsToDelete);
        blogCategoryMapper.deleteBatchIds(idsToDelete);

        // 处理博客表
        for (String path : pathsToDelete) {
            if ("delete".equals(mode)) blogMapper.deleteByCategory(familyId, path);
            else blogMapper.clearCategory(familyId, path);
        }
    }

    private void collectChildPaths(List<Map<String, Object>> tree, Long parentId, Long excludeId,
                                   String parentPath, List<String> paths) {
        for (Map<String, Object> node : tree) {
            if (parentId.equals(node.get("parentId")) && !excludeId.equals(node.get("id"))) {
                paths.add((String) node.get("path"));
                collectChildPaths(tree, (Long) node.get("id"), excludeId, (String) node.get("path"), paths);
            }
        }
    }

    private void collectChildIds(List<Map<String, Object>> tree, Long parentId, List<Long> ids) {
        for (Map<String, Object> node : tree) {
            if (parentId.equals(node.get("parentId"))) {
                ids.add((Long) node.get("id"));
                collectChildIds(tree, (Long) node.get("id"), ids);
            }
        }
    }

    /** 详情:校验可见性与家庭归属后累加浏览量(跨家庭或不可见返回 404) */
    public Blog getDetail(Long id, Long familyId, Long currentUserId, boolean isOwner) {
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        boolean sameFamily = familyId != null && familyId.equals(blog.getFamilyId());
        boolean isAuthor = currentUserId != null && currentUserId.equals(blog.getAuthorId());
        boolean famOwner = isOwner && sameFamily;
        if (!DictConst.VIS_PUBLIC.equals(blog.getVisibility()) && !sameFamily) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (DictConst.VIS_PRIVATE.equals(blog.getVisibility()) && !isAuthor && !famOwner) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!DictConst.BLOG_PUBLISHED.equals(blog.getStatus()) && !isAuthor && !famOwner) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        blogMapper.incrViewCount(id);
        return blog;
    }

    /** 新建博客:归属当前家庭,默认草稿状态、家庭可见 */
    public Blog create(Long authorId, Long familyId, BlogDTO dto) {
        Blog blog = new Blog();
        blog.setTitle(dto.getTitle());
        blog.setContent(dto.getContent());
        blog.setCoverImage(dto.getCoverImage());
        blog.setTags(dto.getTags());
        blog.setAuthorId(authorId);
        blog.setFamilyId(familyId);
        blog.setStatus(DictConst.blogStatus(dto.getStatus()));
        blog.setVisibility(DictConst.visibility(dto.getVisibility()));
        // 空分类统一落"未分类",保证列表分类计数可见
        blog.setCategory(StringUtils.hasText(dto.getCategory()) ? dto.getCategory() : CATEGORY_UNCATEGORIZED);
        blog.setViewCount(0);
        blogMapper.insert(blog);
        pointsService.addRecord(authorId, familyId, "REWARD", PointsService.REWARD_BLOG, "发布博客");
        return blog;
    }

    /** 更新博客:仅作者本人可改,可部分更新标签/状态/可见性 */
    public Blog update(Long id, Long currentUserId, BlogDTO dto) {
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!blog.getAuthorId().equals(currentUserId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        blog.setTitle(dto.getTitle());
        blog.setContent(dto.getContent());
        blog.setCoverImage(dto.getCoverImage());
        if (dto.getTags() != null) blog.setTags(dto.getTags());
        // 分类传了但为空串(前端清空选择)= 移入"未分类";null = 不修改
        if (dto.getCategory() != null) blog.setCategory(StringUtils.hasText(dto.getCategory()) ? dto.getCategory() : CATEGORY_UNCATEGORIZED);
        if (dto.getStatus() != null) blog.setStatus(DictConst.blogStatus(dto.getStatus()));
        if (dto.getVisibility() != null) blog.setVisibility(DictConst.visibility(dto.getVisibility()));
        blogMapper.updateById(blog);
        return blog;
    }

    /** 删除博客:作者本人或家长可删 */
    public void delete(Long id, Long currentUserId, boolean isOwner) {
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!isOwner && !blog.getAuthorId().equals(currentUserId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        blogMapper.deleteById(id);
    }
}
