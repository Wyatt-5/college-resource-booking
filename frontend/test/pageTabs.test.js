import { test } from 'node:test'
import assert from 'node:assert/strict'
import { nextTabAfterClose } from '../src/lib/pageTabs.js'

test('关闭标签时优先回到左侧页面，最后一个关闭后回到首页', () => {
  const tabs = [{ key: 'locations' }, { key: 'items' }, { key: 'mine' }]
  assert.equal(nextTabAfterClose(tabs, 'items', 'locations'), 'locations')
  assert.equal(nextTabAfterClose(tabs, 'locations', 'locations'), 'items')
  assert.equal(nextTabAfterClose([{ key: 'mine' }], 'mine', 'locations'), 'locations')
  assert.equal(nextTabAfterClose([{ key: 'approval' }], 'approval', 'itemStock'), 'itemStock')
})
