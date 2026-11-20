export const HANDOVER_STEPS = ['确认领取', '提交归还', '管理员核验', '完成']

const CURRENT_STEP = {
  NOT_PICKED_UP: 0,
  CHECKED_OUT: 1,
  RETURN_SUBMITTED: 2,
  RETURN_REJECTED: 1,
  COMPLETED: 3,
}

export function handoverStepState(status, index) {
  const current = CURRENT_STEP[status]
  if (current == null) return 'upcoming'
  if (status === 'COMPLETED' || index < current) return 'done'
  if (index === current) return status === 'RETURN_REJECTED' ? 'rejected' : 'current'
  return 'upcoming'
}

export function reservationBadgeClass(reservation) {
  if (reservation?.status === 'CONFIRMED' && reservation.handoverStatus) {
    return {
      NOT_PICKED_UP: 'badge-pending',
      CHECKED_OUT: 'badge-normal',
      RETURN_SUBMITTED: 'badge-pending',
      RETURN_REJECTED: 'badge-reject',
      COMPLETED: 'badge-ok',
    }[reservation.handoverStatus] || 'badge-normal'
  }
  return {
    CONFIRMED: 'badge-ok',
    PENDING_APPROVAL: 'badge-pending',
    REJECTED: 'badge-reject',
    CANCELLED: 'badge-off',
    EXPIRED: 'badge-off',
  }[reservation?.status] || 'badge-normal'
}

export function handoverGuidance(status) {
  return {
    NOT_PICKED_UP: '下一步：在预约时段内按标签填写实物编号并确认领取。',
    CHECKED_OUT: '下一步：归还时拍摄物品整体和主要配件，并填写必要备注。',
    RETURN_SUBMITTED: '材料已提交；管理员核验前，实物仍保持占用。',
    RETURN_REJECTED: '请根据核验意见补拍照片并重新提交。',
    COMPLETED: '管理员已核验通过，实物已经恢复可借。',
  }[status] || ''
}
