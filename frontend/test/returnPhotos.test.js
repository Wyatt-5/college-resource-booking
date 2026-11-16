import { test } from 'node:test'
import assert from 'node:assert/strict'
import { appendReturnPhotos } from '../src/lib/returnPhotos.js'
const photo = (name, extra = {}) => ({ name, type: 'image/png', size: 12, lastModified: 1, ...extra })
test('photo selection appends across batches and deduplicates inside the same batch', () => {
  const a = photo('a.png'), b = photo('b.png')
  assert.deepEqual(appendReturnPhotos([a], [b, a, b]), [a, b])
})
test('invalid batch preserves existing photos and respects size and format limits', () => {
  const existing = [photo('a.png')]
  assert.throws(() => appendReturnPhotos(existing, [photo('b.png'), photo('x.exe', { type: 'application/octet-stream' })]), /格式不支持/)
  assert.throws(() => appendReturnPhotos(existing, [photo('big.png', { size: 5 * 1024 * 1024 + 1 })]), /超过5MB/)
  assert.equal(existing.length, 1)
  assert.equal(appendReturnPhotos([], [photo('limit.png', { size: 5 * 1024 * 1024 })]).length, 1)
})
test('six distinct photos are allowed and removing one makes room for a replacement', () => {
  const six = Array.from({ length: 6 }, (_, i) => photo(`${i}.png`))
  assert.equal(appendReturnPhotos(six, [six[0]]).length, 6)
  assert.throws(() => appendReturnPhotos(six, [photo('new.png')]), /最多上传6张/)
  assert.equal(appendReturnPhotos(six.slice(1), [photo('new.png')]).length, 6)
})
