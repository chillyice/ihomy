/** 新建分类时父级传数字 ID，文章保存时使用完整路径。 */
export function blogCategoryChoices(categories, legacyPath) {
  const children = new Map()
  for (const item of categories) {
    const parentId = item.parentId || 0
    if (!children.has(parentId)) children.set(parentId, [])
    children.get(parentId).push(item)
  }
  const build = (parentId, key) =>
    (children.get(parentId) || []).map((item) => ({
      value: item[key],
      label: item.name,
      children: children.has(item.id) ? build(item.id, key) : undefined,
    }))
  const articles = build(0, 'path')
  if (legacyPath && !categories.some((c) => c.path === legacyPath)) articles.push({ value: legacyPath, label: legacyPath })
  return { articles, parents: build(0, 'id') }
}

export async function addBlogCategory(api, categories, name, parentId) {
  await api.addCategory(name, parentId)
  const parent = categories.find((item) => item.id === parentId)
  return parent ? parent.path + '/' + name : name
}
