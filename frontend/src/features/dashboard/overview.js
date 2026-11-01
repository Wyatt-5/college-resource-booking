// Use the public catalog for dashboard numbers, including later pages as inventory grows.
export async function loadResourceOverview({ getLocations, getPublicItems }) {
  const locations = await getLocations()
  const items = []
  const size = 100
  let page = 0
  let total = 0
  do {
    const result = await getPublicItems({ page, size })
    if (!Array.isArray(result.content) || !Number.isSafeInteger(result.totalElements)) {
      throw new Error('资源统计接口格式不符，请刷新后重试。')
    }
    if (result.content.length === 0 && items.length < result.totalElements) {
      throw new Error('资源统计分页不完整，请刷新后重试。')
    }
    items.push(...result.content)
    total = result.totalElements
    page += 1
  } while (items.length < total)

  const byLocation = new Map(locations.map((location) => [location.id, {
    itemCount: 0,
    availableItemCount: 0,
    availableUnits: 0,
  }]))
  for (const item of items) {
    const entry = byLocation.get(item.locationId)
    if (!entry) continue
    entry.itemCount += 1
    if ((item.unitSummary?.available ?? 0) > 0) entry.availableItemCount += 1
    entry.availableUnits += item.unitSummary?.available ?? 0
  }

  return {
    locations: locations.map((location) => ({ ...location, ...byLocation.get(location.id) })),
    totals: {
      locations: locations.filter((location) => location.status === 'ACTIVE').length,
      items: items.length,
      availableItems: items.filter((item) => (item.unitSummary?.available ?? 0) > 0).length,
      availableUnits: items.reduce((sum, item) => sum + (item.unitSummary?.available ?? 0), 0),
    },
  }
}
