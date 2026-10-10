const allowedElements = new Set(['svg', 'g', 'path', 'circle', 'ellipse', 'rect', 'line', 'polyline', 'polygon', 'defs', 'clipPath', 'mask', 'linearGradient', 'radialGradient', 'stop'])
const allowedAttributes = new Set([
  'xmlns', 'viewBox', 'width', 'height', 'fill', 'fill-rule', 'clip-rule', 'stroke', 'stroke-width',
  'stroke-linecap', 'stroke-linejoin', 'stroke-dasharray', 'stroke-dashoffset', 'opacity', 'fill-opacity',
  'stroke-opacity', 'cx', 'cy', 'r', 'rx', 'ry', 'x', 'y', 'x1', 'y1', 'x2', 'y2', 'd', 'points',
  'transform', 'id', 'offset', 'stop-color', 'stop-opacity', 'gradientUnits', 'gradientTransform',
  'clip-path', 'mask', 'href', 'xlink:href',
])

export function sanitizeSvgText(value) {
  if (typeof DOMParser === 'undefined' || typeof XMLSerializer === 'undefined') return ''
  const source = String(value || '').replace(/^\uFEFF/, '').trim()
  if (!/^<svg(?:\s|>)/i.test(source)) return ''
  try {
    const document = new DOMParser().parseFromString(source, 'image/svg+xml')
    const root = document.documentElement
    if (root.localName !== 'svg' || document.querySelector('parsererror')) return ''
    cleanElement(root)
    root.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
    return new XMLSerializer().serializeToString(root)
  } catch { return '' }
}

function cleanElement(element) {
  for (const child of [...element.children]) {
    if (!allowedElements.has(child.localName)) child.remove()
    else cleanElement(child)
  }
  for (const attribute of [...element.attributes]) {
    const name = attribute.name
    const value = attribute.value.trim()
    const safeReference = /^url\(#[\w.-]+\)$/i.test(value) || /^#[\w.-]+$/.test(value)
    const unsafeValue = /javascript:|data:|https?:|url\((?!#[\w.-]+\))/i.test(value)
    if (!allowedAttributes.has(name) || unsafeValue || ((name === 'href' || name === 'xlink:href') && !safeReference)) {
      element.removeAttribute(name)
    }
  }
}
