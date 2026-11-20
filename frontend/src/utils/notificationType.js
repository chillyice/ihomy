const typeLabels = new Map([
  ['reply', 'notify.typeReply'],
  ['system', 'notify.typeSystem'],
  ['family_notice', 'notify.typeFamily'],
  ['item_expiry', 'notify.typeExpiry'],
  ['reminder', 'notify.typeReminder'],
  ['anniversary', 'notify.typeAnniversary'],
])

export const notificationTypeKey = (type) => typeLabels.get(type) || 'notify.typeComment'
