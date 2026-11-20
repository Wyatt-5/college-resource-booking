export const navigation = {
  user: [
    { key: 'booking', label: '预约', children: [
      { key: 'items', label: '物品预约' },
      { key: 'rooms', label: '地点预约' },
    ] },
    { key: 'personal', label: '我的', children: [
      { key: 'myReservations', label: '我的预约' },
      { key: 'myFeedback', label: '我的反馈' },
      { key: 'myProfile', label: '我的资料' },
    ] },
    { key: 'locations', label: '存放地点' },
  ],
  admin: [
    { key: 'approval', label: '审核工作台' },
    { key: 'returnCheck', label: '归还核验' },
    { key: 'returnReminders', label: '未归还提醒' },
    { key: 'management', label: '管理', children: [
      { key: 'itemStock', label: '库存管理' },
      { key: 'spaceManage', label: '地点管理' },
      { key: 'account', label: '账号管理' },
      { key: 'announcementAdmin', label: '公告管理' },
    ] },
    { key: 'feedbackAdmin', label: '用户反馈' },
  ],
}

export const sections = Object.entries(navigation).flatMap(([group, entries]) =>
  entries.flatMap(entry => entry.children
    ? entry.children.map(child => ({ ...child, group, parent: entry.key }))
    : [{ ...entry, group }]))
