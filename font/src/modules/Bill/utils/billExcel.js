import readExcelFile from 'read-excel-file/browser'
import { findBillHeaderIndex } from './billCsv.js'

function cellText(value) {
  if (value == null) return ''
  if (value instanceof Date) return value.toISOString().slice(0, 19)
  return String(value).trim()
}

export async function parseBillExcel(file) {
  const sheets = await readExcelFile(file)
  return sheets.map(({ sheet, data }) => {
    const firstRow = data.findIndex(row => row.some(value => cellText(value)))
    if (firstRow < 0) return null
    const rows = data.slice(firstRow).map(row => row.map(cellText))
    const headerIndex = findBillHeaderIndex(rows)
    return {
      name: sheet,
      headers: rows[headerIndex],
      rows: rows.slice(headerIndex + 1).map((cells, index) => ({ cells, line: firstRow + headerIndex + index + 2 }))
        .filter(row => row.cells.some(Boolean)),
    }
  }).filter(sheet => sheet?.rows.length)
}
