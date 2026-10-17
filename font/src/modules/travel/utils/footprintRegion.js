export function getFootprintRegion(footprint) {
  const path = Array.isArray(footprint?.regionPaths) ? footprint.regionPaths : []
  const namedPath = path.filter(node => Number(node.level) > 0 && node.name)
  const deepest = namedPath.at(-1)
  const city = namedPath.find(node => Number(node.level) === 2) || deepest

  return {
    regionName: namedPath.map(node => node.name).filter((name, index, names) => index === 0 || name !== names[index - 1]).join(''),
    provinceName: namedPath.find(node => Number(node.level) === 1)?.name || '',
    cityName: city?.name || '',
    cityRegionId: city?.id || ''
  }
}
