const PAGE_SIZE = 100

export async function fetchAllPages(fetchPage, filters = {}) {
  const records = []
  let pageNum = 1
  let total = Infinity

  while (records.length < total) {
    const result = await fetchPage({ ...filters, pageNum, pageSize: PAGE_SIZE })
    const pageRecords = result?.data || []
    records.push(...pageRecords)
    total = Number(result?.page?.total ?? records.length)
    if (pageRecords.length < PAGE_SIZE) break
    pageNum += 1
  }

  return records
}

export function departureYearFilter(year) {
  if (year == null || year === 'all' || year === '') return {}
  return {
    departureTimeStart: `${year}-01-01T00:00:00`,
    departureTimeEnd: `${year}-12-31T23:59:59`
  }
}
