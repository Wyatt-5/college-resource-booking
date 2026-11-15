import { test } from 'node:test'
import assert from 'node:assert/strict'
import { HANDOVER_STEPS, handoverGuidance, handoverStepState, reservationBadgeClass } from '../src/lib/handoverState.js'

test('handover progress exposes pickup, return, verification and completion in order', () => {
  assert.deepEqual(HANDOVER_STEPS, ['确认领取', '提交归还', '管理员核验', '完成'])
  assert.deepEqual(HANDOVER_STEPS.map((_, index) => handoverStepState('RETURN_SUBMITTED', index)),
    ['done', 'done', 'current', 'upcoming'])
  assert.deepEqual(HANDOVER_STEPS.map((_, index) => handoverStepState('COMPLETED', index)),
    ['done', 'done', 'done', 'done'])
})

test('rejected return is highlighted and sends the user back to resubmission', () => {
  const reservation = { status: 'CONFIRMED', handoverStatus: 'RETURN_REJECTED' }
  assert.equal(reservationBadgeClass(reservation), 'badge-reject')
  assert.equal(handoverStepState('RETURN_REJECTED', 1), 'rejected')
  assert.match(handoverGuidance('RETURN_REJECTED'), /补拍照片并重新提交/)
})

test('non-handover reservations retain their booking status colors', () => {
  assert.equal(reservationBadgeClass({ status: 'PENDING_APPROVAL' }), 'badge-pending')
  assert.equal(reservationBadgeClass({ status: 'REJECTED' }), 'badge-reject')
})
