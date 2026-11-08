export function createRequestKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  const random = new Uint32Array(4)
  if (globalThis.crypto?.getRandomValues) globalThis.crypto.getRandomValues(random)
  else for (let index = 0; index < random.length; index += 1) random[index] = Math.floor(Math.random() * 0x100000000)
  return `web-${Date.now().toString(36)}-${Array.from(random, value => value.toString(36)).join('-')}`
}
