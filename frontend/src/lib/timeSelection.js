// Represent one continuous half-open range; clicking inside the middle would split it.
export function toggleTimeSlot(range, point, width) {
  if (!range) return { start: point, end: point + width }
  const { start, end } = range
  if (point >= start && point < end) {
    if (end - start === width) return null
    if (point === start) return { start: start + width, end }
    if (point + width === end) return { start, end: end - width }
    return { ...range, blocked: true }
  }
  if (point + width === start) return { start: point, end }
  if (point === end) return { start, end: point + width }
  return { start: point, end: point + width }
}
