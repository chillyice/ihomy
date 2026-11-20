import { describe, expect, it, vi } from 'vitest'
import { addBlogCategory, blogCategoryChoices } from './blogCategories'

const categories = [
  { id: 6, parentId: 3, name: '江南', path: '旅行/江南' },
  { id: 3, parentId: null, name: '旅行', path: '旅行' },
]

describe('blog editor category selection', () => {
  it('uses the parent option as an API id and selects the full article path after creation', async () => {
    const api = { addCategory: vi.fn().mockResolvedValue(null) }
    const choices = blogCategoryChoices(categories)
    const selectedParent = choices.parents[0].children[0].value
    const selectedPath = await addBlogCategory(api, categories, '苏州', selectedParent)
    expect(api.addCategory).toHaveBeenCalledWith('苏州', 6)
    expect(selectedPath).toBe('旅行/江南/苏州')
    expect(choices.articles[0].children[0].value).toBe('旅行/江南')
  })

  it('allows legacy paths only as article categories and creates a root category without a parent', async () => {
    const api = { addCategory: vi.fn().mockResolvedValue(null) }
    const choices = blogCategoryChoices(categories, '旧文章')
    expect(choices.parents.map((c) => c.value)).toEqual([3])
    expect(choices.articles.map((c) => c.value)).toEqual(['旅行', '旧文章'])
    await expect(addBlogCategory(api, categories, '生活', null)).resolves.toBe('生活')
    expect(api.addCategory).toHaveBeenCalledWith('生活', null)
  })
})
