export function getCssThemeColor(name, fallback = '') {
  if (typeof window === 'undefined') return fallback
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

export function getCssThemePalette(prefix, count, fallback = []) {
  return Array.from({ length: count }, (_, index) =>
    getCssThemeColor(`--${prefix}-chart-${index + 1}`, fallback[index] || '#258b88'),
  )
}
