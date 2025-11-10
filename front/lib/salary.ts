export type SalaryInfo = {
  minK: number
  maxK: number
  months: number
  medianK: number
  annualTotal: number
}

/**
 * 解析薪资字符串：如 "20-40K", "35-65K·16薪", "30K·15薪"；包含 "面议" 则返回 undefined
 */
export function parseSalary(raw?: string): SalaryInfo | undefined {
  if (!raw) return undefined
  let s = raw.trim()
  if (!s || s.includes("面议")) return undefined
  s = s.replace(/\s+/g, "")

  let months = 12
  const monthsMatch = s.match(/([0-9]+)薪/)
  if (monthsMatch) {
    months = Number(monthsMatch[1]) || 12
    // 去掉薪资后缀以便解析区间
    s = s.slice(0, s.indexOf(monthsMatch[0]))
    // 某些源数据在 K 后还有多余符号（如 "30-60K-"），此处截断到最后的 K
    const kIndex = Math.max(s.lastIndexOf("K"), s.lastIndexOf("k"))
    if (kIndex >= 0) s = s.slice(0, kIndex + 1)
  }

  let minK: number | undefined
  let maxK: number | undefined

  const range = s.match(/^(\d+)-(\d+)[Kk]$/)
  const single = s.match(/^(\d+)[Kk]$/)
  if (range) {
    minK = Number(range[1])
    maxK = Number(range[2])
  } else if (single) {
    minK = Number(single[1])
    maxK = minK
  } else {
    // 宽松解析：仅保留数字、K 与连字符
    const cleaned = s.replace(/[^0-9Kk\-]/g, "")
    const r2 = cleaned.match(/^(\d+)-(\d+)[Kk]$/)
    const s2 = cleaned.match(/^(\d+)[Kk]$/)
    if (r2) {
      minK = Number(r2[1])
      maxK = Number(r2[2])
    } else if (s2) {
      minK = Number(s2[1])
      maxK = minK
    }
  }

  if (minK == null || maxK == null) return undefined

  const medianK = (minK + maxK) / 2
  return {
    minK,
    maxK,
    months,
    medianK,
    annualTotal: Math.round(medianK * 1000 * months),
  }
}