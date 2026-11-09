import { test } from 'node:test'
import assert from 'node:assert/strict'
import { toggleTimeSlot } from '../src/lib/timeSelection.js'

test('点击、重复点击和相邻时段保持连续', () => {
  let selection = toggleTimeSlot(null, 8, 1)
  assert.deepEqual(selection, { start: 8, end: 9 })
  selection = toggleTimeSlot(selection, 9, 1)
  assert.deepEqual(selection, { start: 8, end: 10 })
  selection = toggleTimeSlot(selection, 8, 1)
  assert.deepEqual(selection, { start: 9, end: 10 })
  assert.equal(toggleTimeSlot(selection, 9, 1), null)
  assert.deepEqual(toggleTimeSlot({ start: 8, end: 11 }, 9, 1), { start: 8, end: 11, blocked: true })
  assert.deepEqual(toggleTimeSlot({ start: 8, end: 9 }, 12, 1), { start: 12, end: 13 })
})
