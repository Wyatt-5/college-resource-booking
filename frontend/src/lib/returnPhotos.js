const TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
export function appendReturnPhotos(existing, selected) {
  const files = [...existing]
  const keys = new Set(existing.map(file => `${file.name}:${file.size}:${file.lastModified}`))
  for (const file of selected) {
    if (!TYPES.has(file.type)) throw new Error(`「${file.name}」格式不支持，请选择 JPG、PNG 或 WebP。`)
    if (file.size > 5 * 1024 * 1024) throw new Error(`「${file.name}」超过5MB，请压缩后重试。`)
    const key = `${file.name}:${file.size}:${file.lastModified}`
    if (!keys.has(key)) { files.push(file); keys.add(key) }
  }
  if (files.length > 6) throw new Error('最多上传6张照片。')
  return files
}
