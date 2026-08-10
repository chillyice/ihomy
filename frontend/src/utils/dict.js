// V3.9 数据字典前端映射: 后端 status/visibility 等返回大写英文单词(与 sys_dict_item 对应),
// 前端据此渲染中文标签。表单提交仍可发数字(DTO 端转换)。
export const DICT = {
  visibility: { PRIVATE: '仅自己', MEMBERS: '指定成员', GROUPS: '指定群组', FAMILY: '家庭可见', PUBLIC: '公开' },
  blogStatus: { DRAFT: '草稿', PUBLISHED: '已发布', HIDDEN: '已隐藏' },
  userStatus: { ACTIVE: '正常', DISABLED: '禁用' },
  planStatus: { ACTIVE: '进行中', DONE: '已完成', CANCELLED: '已取消' },
  taskStatus: { OPEN: '待领取', IN_PROGRESS: '进行中', REVIEW: '待确认', DONE: '已完成', CANCELLED: '已取消' },
  rewardType: { NONE: '无奖励', POINTS: '积分', ITEM: '物品' },
  wishStatus: { PENDING: '待实现', ACHIEVED: '已实现', ABANDONED: '已放弃' },
  videoWishStatus: { PENDING: '待入库', IMPORTED: '已入库' },
  orderStatus: { PENDING: '待核销', REDEEMED: '已核销' },
  bookType: { EXPENSE: '支出', INCOME: '收入', TRANSFER: '转账' },
  repeatType: { ONCE: '一次性', DAILY: '每日', WEEKLY: '每周', MONTHLY: '每月' },
  annRecurring: { ONCE: '单次', YEARLY: '每年' }
}

// 取单词的含义;未知值回退原值,避免页面空白
export const dictText = (group, value) => (DICT[group] && DICT[group][value]) || value || ''