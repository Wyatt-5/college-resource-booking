export function nextTabAfterClose(groupTabs, closingKey, fallback) {
  const index = groupTabs.findIndex(tab => tab.key === closingKey)
  if (index < 0) return fallback
  return groupTabs[index - 1]?.key || groupTabs[index + 1]?.key || fallback
}
