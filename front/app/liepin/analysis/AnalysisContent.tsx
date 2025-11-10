"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select } from "@/components/ui/select"
import { Label } from "@/components/ui/label"
import PageHeader from "@/app/components/PageHeader"
import { BiRefresh, BiDownload, BiBarChart, BiLineChart, BiPieChart, BiBriefcase } from "react-icons/bi"
import { parseSalary } from "@/lib/salary"

type NameValue = { name: string; value: number }
type BucketValue = { bucket: string; value: number }

type StatsResponse = {
  kpi: {
    total: number
    delivered: number
    pending: number
    filtered: number
    failed: number
    avgMonthlyK?: number | null
  }
  charts: {
    byStatus: NameValue[]
    byCity: NameValue[]
    byIndustry: NameValue[]
    byCompany: NameValue[]
    byExperience: NameValue[]
    byDegree: NameValue[]
    salaryBuckets: BucketValue[]
    dailyTrend: NameValue[]
    hrActivity: NameValue[]
  }
}

type LiepinJob = {
  jobId: number
  compName?: string
  compIndustry?: string
  compScale?: string
  jobTitle?: string
  jobSalaryText?: string
  jobArea?: string
  jobEduReq?: string
  jobExpReq?: string
  jobPublishTime?: string
  jobLink?: string
  hrName?: string
  hrTitle?: string
  delivered?: number
  createTime?: string
}

type PagedResult = {
  items: LiepinJob[]
  total: number
  page: number
  size: number
}

const API_BASE = "http://localhost:8888"
const CATEGORY_COLORS = [
  "#3b82f6",
  "#10b981",
  "#f59e0b",
  "#ef4444",
  "#6366f1",
  "#22c55e",
  "#fb7185",
  "#a78bfa",
  "#f97316",
  "#06b6d4",
  "#4ade80",
  "#2dd4bf",
  "#f472b6",
  "#64748b",
]

function ChartCanvas({
  type,
  labels,
  data,
  title,
  color = "#3b82f6",
  colors,
}: {
  type: "pie" | "bar" | "line"
  labels: string[]
  data: number[]
  title?: string
  color?: string
  colors?: string[]
}) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const chartRef = useRef<any | null>(null)
  const toSolid = (hex: string) => hex

  async function ensureChart(): Promise<any> {
    if (typeof window !== "undefined" && (window as any).Chart) return (window as any).Chart
    return new Promise((resolve, reject) => {
      const existing = document.querySelector("script[data-chartjs-cdn='true']") as HTMLScriptElement | null
      if (existing) {
        existing.addEventListener("load", () => resolve((window as any).Chart))
        existing.addEventListener("error", () => reject(new Error("Chart.js CDN load error")))
        return
      }
      const script = document.createElement("script")
      script.src = "https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js"
      script.async = true
      script.setAttribute("data-chartjs-cdn", "true")
      script.addEventListener("load", () => resolve((window as any).Chart))
      script.addEventListener("error", () => reject(new Error("Chart.js CDN load error")))
      document.head.appendChild(script)
    })
  }

  useEffect(() => {
    const ctx = canvasRef.current?.getContext("2d")
    if (!ctx) return

    if (chartRef.current) {
      chartRef.current.destroy()
      chartRef.current = null
    }

    let cancelled = false

    const pieColorsBase = [
      "#3b82f6",
      "#10b981",
      "#f59e0b",
      "#ef4444",
      "#6366f1",
      "#22c55e",
      "#fb7185",
      "#a78bfa",
      "#f97316",
      "#06b6d4",
    ]

    const backgroundColor = (() => {
      if (type === "pie") {
        const arr = (colors && colors.length ? colors : pieColorsBase).slice(0, labels.length)
        return arr
      }
      if (type === "bar" && colors && colors.length) {
        return colors.slice(0, data.length).map((c) => toSolid(c))
      }
      return toSolid(color ?? "#3b82f6")
    })()

    const borderColor = (() => {
      if (type === "pie") return undefined
      if (type === "bar" && colors && colors.length) return colors.slice(0, data.length)
      return color
    })()

    const dataset: any = {
      label: title || "",
      data,
      backgroundColor,
      borderColor,
    }

    if (type === "line") {
      dataset.fill = false
      dataset.pointBackgroundColor = toSolid(color)
      dataset.pointBorderColor = toSolid(color)
    }

    ;(async () => {
      try {
        const Chart = await ensureChart()
        if (cancelled) return
        chartRef.current = new Chart(ctx, {
          type,
          data: { labels, datasets: [dataset] },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
              legend: { display: type === "pie" },
              title: { display: !!title, text: title },
            },
            scales: type !== "pie" ? { x: { ticks: { autoSkip: true } }, y: { beginAtZero: true } } : undefined,
          },
        })
      } catch (error) {
        console.error("Failed to create chart:", error)
      }
    })()

    return () => {
      cancelled = true
      if (chartRef.current) {
        chartRef.current.destroy()
        chartRef.current = null
      }
    }
  }, [type, labels, data, title, color, colors])

  return <canvas ref={canvasRef} className="w-full h-64" />
}

export default function AnalysisContent({ showHeader = false }: { showHeader?: boolean }) {
  const [stats, setStats] = useState<StatsResponse | null>(null)
  const [loadingStats, setLoadingStats] = useState(true)

  const [items, setItems] = useState<LiepinJob[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [inputPage, setInputPage] = useState<number | string>(1)
  const [inputSize, setInputSize] = useState<number | string>(20)

  const [statuses, setStatuses] = useState<string[]>([])
  const [location, setLocation] = useState<string>("")
  const [experience, setExperience] = useState<string>("")
  const [degree, setDegree] = useState<string>("")
  const [minK, setMinK] = useState<string>("")
  const [maxK, setMaxK] = useState<string>("")
  const [keyword, setKeyword] = useState<string>("")
  const [loadingList, setLoadingList] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [detailJob, setDetailJob] = useState<LiepinJob | null>(null)
  const [computedSalaryBuckets, setComputedSalaryBuckets] = useState<BucketValue[]>([])

  const statusOptions = ["未投递", "已投递"]

  useEffect(() => {
    loadStats()
  }, [])

  useEffect(() => { setInputPage(page) }, [page])
  useEffect(() => { setInputSize(size) }, [size])

  const formatDateOnly = (s?: string) => {
    if (!s) return ""
    const d = new Date(s)
    if (!isNaN(d.getTime())) {
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, "0")
      const day = String(d.getDate()).padStart(2, "0")
      return `${y}-${m}-${day}`
    }
    return s.slice(0, 10)
  }

  const loadList = async (toPage = page, toSize = size) => {
    const params = new URLSearchParams()
    if (statuses.length) params.set("statuses", statuses.join(","))
    if (location) params.set("location", location)
    if (experience) params.set("experience", experience)
    if (degree) params.set("degree", degree)
    if (minK) params.set("minK", String(Number(minK)))
    if (maxK) params.set("maxK", String(Number(maxK)))
    if (keyword) params.set("keyword", keyword)
    params.set("page", String(toPage))
    params.set("size", String(toSize))

    try {
      setLoadingList(true)
      const res = await fetch(`${API_BASE}/api/liepin/list?${params.toString()}`)
      const data: PagedResult = await res.json()
      setItems(data.items || [])
      setTotal(data.total || 0)
      setPage(data.page || toPage)
      setSize(data.size || toSize)
    } catch (e) {
      console.error("fetch liepin list failed", e)
    } finally {
      setLoadingList(false)
    }
  }

  const loadStats = async () => {
    const params = new URLSearchParams()
    if (statuses.length) params.set("statuses", statuses.join(","))
    if (location) params.set("location", location)
    if (experience) params.set("experience", experience)
    if (degree) params.set("degree", degree)
    if (minK) params.set("minK", String(Number(minK)))
    if (maxK) params.set("maxK", String(Number(maxK)))
    if (keyword) params.set("keyword", keyword)

    try {
      setLoadingStats(true)
      const res = await fetch(`${API_BASE}/api/liepin/stats?${params.toString()}`)
      const data: StatsResponse = await res.json()
      setStats(data)
    } catch (e) {
      console.error("fetch liepin stats failed", e)
    } finally {
      setLoadingStats(false)
    }
  }

  useEffect(() => { loadList(1, size) }, [])

  const exportCSV = async () => {
    try {
      setExporting(true)
      const baseParams = new URLSearchParams()
      if (statuses.length) baseParams.set("statuses", statuses.join(","))
      if (location) baseParams.set("location", location)
      if (experience) baseParams.set("experience", experience)
      if (degree) baseParams.set("degree", degree)
      if (minK) baseParams.set("minK", String(Number(minK)))
      if (maxK) baseParams.set("maxK", String(Number(maxK)))
      if (keyword) baseParams.set("keyword", keyword)

      const pageSize = 1000
      let currentPage = 1
      let all: LiepinJob[] = []
      let totalCount = 0

      while (true) {
        const params = new URLSearchParams(baseParams)
        params.set("page", String(currentPage))
        params.set("size", String(pageSize))
        const res = await fetch(`${API_BASE}/api/liepin/list?${params.toString()}`)
        const data: PagedResult = await res.json()
        const chunk = data.items || []
        if (currentPage === 1) totalCount = data.total || chunk.length
        all = all.concat(chunk)
        if (all.length >= totalCount || chunk.length === 0) break
        currentPage += 1
      }

      const header = [
        "公司名称",
        "岗位名称",
        "薪资",
        "工作地点",
        "经验",
        "学历",
        "HR",
        "投递状态",
        "链接",
        "创建时间",
      ]
      const rows = all.map((it) => [
        it.compName || "",
        it.jobTitle || "",
        it.jobSalaryText || "",
        it.jobArea || "",
        it.jobExpReq || "",
        it.jobEduReq || "",
        it.hrName || "",
        (it.delivered === 1 ? "已投递" : "未投递"),
        it.jobLink || "",
        it.createTime || "",
      ])
      const csv = [header, ...rows]
        .map((r) => r.map((v) => (String(v).includes(",") ? `"${String(v).replace(/"/g, '""')}"` : String(v))).join(","))
        .join("\n")
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" })
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `liepin_jobs_${new Date().toISOString().slice(0, 10)}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      console.error("export CSV failed", e)
      alert("导出失败，请稍后重试")
    } finally {
      setExporting(false)
    }
  }

  // 当后端的薪资分布为空或全零时，使用全量分页数据计算分布
  const refreshComputedSalaryBuckets = async () => {
    try {
      const baseParams = new URLSearchParams()
      if (statuses.length) baseParams.set("statuses", statuses.join(","))
      if (location) baseParams.set("location", location)
      if (experience) baseParams.set("experience", experience)
      if (degree) baseParams.set("degree", degree)
      if (minK) baseParams.set("minK", String(Number(minK)))
      if (maxK) baseParams.set("maxK", String(Number(maxK)))
      if (keyword) baseParams.set("keyword", keyword)

      const pageSize = 1000
      let currentPage = 1
      let totalCount = 0
      const ks: number[] = []

      while (true) {
        const params = new URLSearchParams(baseParams)
        params.set("page", String(currentPage))
        params.set("size", String(pageSize))
        const res = await fetch(`${API_BASE}/api/liepin/list?${params.toString()}`)
        const data: PagedResult = await res.json()
        const chunk = data.items || []
        if (currentPage === 1) totalCount = data.total || chunk.length
        for (const it of chunk) {
          const info = parseSalary(it.jobSalaryText)
          if (info && !isNaN(info.medianK)) ks.push(info.medianK)
        }
        if (currentPage * pageSize >= totalCount || chunk.length === 0) break
        currentPage += 1
      }

      if (!ks.length) { setComputedSalaryBuckets([]); return }
      const buckets: { key: string; min: number; max: number | null }[] = [
        { key: "0-10K", min: 0, max: 10 },
        { key: "10-15K", min: 10, max: 15 },
        { key: "15-20K", min: 15, max: 20 },
        { key: "20-25K", min: 20, max: 25 },
        { key: ">=25K", min: 25, max: null },
      ]
      const counts = new Map<string, number>()
      for (const b of buckets) counts.set(b.key, 0)
      for (const k of ks) {
        const b = buckets.find((x) => (k >= x.min) && (x.max == null ? true : k < x.max))
        if (b) counts.set(b.key, (counts.get(b.key) || 0) + 1)
      }
      setComputedSalaryBuckets(buckets.map((b) => ({ bucket: b.key, value: counts.get(b.key) || 0 })))
    } catch (e) {
      console.error("compute salary buckets failed", e)
      setComputedSalaryBuckets([])
    }
  }

  // 监听筛选变化与统计加载完毕后刷新分布
  useEffect(() => {
    const apiBuckets = stats?.charts?.salaryBuckets || []
    const sum = apiBuckets.reduce((a, b) => a + (b?.value || 0), 0)
    if (apiBuckets.length === 0 || sum === 0) {
      refreshComputedSalaryBuckets()
    } else {
      setComputedSalaryBuckets([])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stats, statuses.join(","), location, experience, degree, minK, maxK, keyword])

  // 移除原“累计趋势”计算逻辑；改为以现有维度（经验）展示折线图

  const badgeClass = (kind: "delivery" | "recruitment", value?: string) => {
    const base = "px-2 py-1 rounded-full text-xs font-medium whitespace-nowrap"
    const v = (value || "").trim()
    if (kind === "delivery") {
      if (v.includes("已投递")) return `${base} bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300`
      return `${base} bg-slate-100 text-slate-700 dark:bg-slate-800/50 dark:text-slate-300`
    }
    return `${base} bg-gray-100 text-gray-700 dark:bg-gray-700/50 dark:text-gray-200`
  }

  const kpiCards = useMemo(() => {
    const k = stats?.kpi
    // 前端容错：当后端未提供均值时，基于当前列表估算月薪均值(K)
    const avgMonthlyKFromItems = (() => {
      if (!items?.length) return undefined
      const ks: number[] = []
      for (const it of items) {
        const info = parseSalary(it.jobSalaryText)
        if (info && !isNaN(info.medianK)) ks.push(info.medianK)
      }
      if (!ks.length) return undefined
      const sum = ks.reduce((a, b) => a + b, 0)
      return Math.round((sum / ks.length) * 10) / 10
    })()
    return [
      { title: "总岗位数", value: k?.total ?? 0 },
      { title: "已投递", value: k?.delivered ?? 0 },
      { title: "未投递", value: k?.pending ?? 0 },
      { title: "平均月薪(K)", value: (k?.avgMonthlyK ?? avgMonthlyKFromItems ?? 0) },
    ]
  }, [stats, items])

  // 前端容错：当图表数据缺失时，基于列表计算分布
  const fallbackSalaryBuckets = useMemo(() => {
    const ks: number[] = []
    for (const it of items) {
      const info = parseSalary(it.jobSalaryText)
      if (info && !isNaN(info.medianK)) ks.push(info.medianK)
    }
    if (!ks.length) return [] as BucketValue[]
    const buckets: { key: string; min: number; max: number | null }[] = [
      { key: "0-10K", min: 0, max: 10 },
      { key: "10-15K", min: 10, max: 15 },
      { key: "15-20K", min: 15, max: 20 },
      { key: "20-25K", min: 20, max: 25 },
      { key: ">=25K", min: 25, max: null },
    ]
    const counts = new Map<string, number>()
    for (const b of buckets) counts.set(b.key, 0)
    for (const k of ks) {
      const b = buckets.find((x) => (k >= x.min) && (x.max == null ? true : k < x.max))
      if (b) counts.set(b.key, (counts.get(b.key) || 0) + 1)
    }
    return buckets.map((b) => ({ bucket: b.key, value: counts.get(b.key) || 0 }))
  }, [items])

  const fallbackByExperience = useMemo(() => {
    if (!items?.length) return [] as NameValue[]
    const dict = new Map<string, number>()
    const norm = (s?: string) => (s || "").trim() || "未知"
    for (const it of items) {
      const k = norm(it.jobExpReq)
      dict.set(k, (dict.get(k) || 0) + 1)
    }
    return Array.from(dict.entries()).map(([name, value]) => ({ name, value }))
  }, [items])

  const fallbackByDegree = useMemo(() => {
    if (!items?.length) return [] as NameValue[]
    const dict = new Map<string, number>()
    const norm = (s?: string) => (s || "").trim() || "不限"
    for (const it of items) {
      const k = norm(it.jobEduReq)
      dict.set(k, (dict.get(k) || 0) + 1)
    }
    return Array.from(dict.entries()).map(([name, value]) => ({ name, value }))
  }, [items])

  // 需求变更：删去 HR 活跃度卡片（保留相关类型定义无需使用）

  return (
    <div className="space-y-8">
      {showHeader && (
        <PageHeader
          icon={<BiBriefcase className="text-2xl" />}
          title="猎聘投递分析"
          subtitle="统计分析猎聘平台的岗位投递数据"
          iconClass="text-white"
          accentBgClass="bg-orange-500"
          actions={
            <div className="flex items-center gap-2">
              <Button onClick={() => { loadList(1, size); loadStats(); }} size="sm" className="rounded-full bg-gradient-to-r from-teal-500 to-green-500 hover:from-teal-600 hover:to-green-600 text-white px-4">
                <BiRefresh className="mr-1" /> 刷新
              </Button>
              <Button onClick={exportCSV} disabled={exporting} size="sm" className="rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-600 hover:to-indigo-600 text-white px-4">
                <BiDownload className="mr-1" /> 导出CSV
              </Button>
            </div>
          }
        />
      )}

      {/* KPI */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        {kpiCards.map((k) => (
          <Card key={k.title} className="border-white/20">
            <CardContent className="pt-6">
              <p className="text-sm font-medium text-muted-foreground mb-1">{k.title}</p>
              <p className="text-3xl font-bold text-primary">{k.value}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 筛选区域 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">筛选条件</CardTitle>
          <CardDescription>根据状态、地区、经验、学历、薪资区间与关键词筛选</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <Label className="block mb-1">状态</Label>
              <div className="flex flex-wrap gap-2">
                {statusOptions.map((s) => (
                  <label key={s} className="inline-flex items-center gap-1 text-sm">
                    <input
                      type="checkbox"
                      checked={statuses.includes(s)}
                      onChange={(e) => {
                        setStatuses((prev) => e.target.checked ? [...prev, s] : prev.filter((x) => x !== s))
                      }}
                    />
                    {s}
                  </label>
                ))}
              </div>
            </div>
            <div>
              <Label htmlFor="location">城市</Label>
              <Input id="location" value={location} onChange={(e) => setLocation(e.target.value)} placeholder="例如：北京" />
            </div>
            <div>
              <Label htmlFor="keyword">关键词</Label>
              <Input id="keyword" value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="公司/岗位/HR" />
            </div>
            <div>
              <Label htmlFor="experience">经验</Label>
              <Select id="experience" value={experience} onChange={(e) => setExperience(e.target.value)}>
                <option value="">不限</option>
                <option value="应届">应届</option>
                <option value="1-3年">1-3年</option>
                <option value="3-5年">3-5年</option>
                <option value="5-10年">5-10年</option>
                <option value="10年以上">10年以上</option>
              </Select>
            </div>
            <div>
              <Label htmlFor="degree">学历</Label>
              <Select id="degree" value={degree} onChange={(e) => setDegree(e.target.value)}>
                <option value="">不限</option>
                <option value="大专">大专</option>
                <option value="本科">本科</option>
                <option value="硕士">硕士</option>
                <option value="博士">博士</option>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <Label htmlFor="minK">最低K</Label>
                <Input id="minK" value={minK} onChange={(e) => setMinK(e.target.value)} placeholder="如：15" />
              </div>
              <div>
                <Label htmlFor="maxK">最高K</Label>
                <Input id="maxK" value={maxK} onChange={(e) => setMaxK(e.target.value)} placeholder="如：30" />
              </div>
            </div>
          </div>
          <div className="mt-4 flex items-center gap-2">
            <Button onClick={() => { loadList(1, size); loadStats(); }} className="rounded-full bg-gradient-to-r from-teal-500 to-green-500 hover:from-teal-600 hover:to-green-600 text-white px-4">
              <BiRefresh className="mr-1" /> 应用筛选
            </Button>
            <Button onClick={exportCSV} disabled={exporting} className="rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-600 hover:to-indigo-600 text-white px-4">
              <BiDownload className="mr-1" /> 导出CSV
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 图表 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart className="text-primary" />按城市</CardTitle>
            <CardDescription>热门城市岗位数排行</CardDescription>
          </CardHeader>
          <CardContent>
            <ChartCanvas type="bar" title="按城市" labels={(stats?.charts?.byCity || []).map(x => x.name)} data={(stats?.charts?.byCity || []).map(x => x.value)} colors={CATEGORY_COLORS} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiPieChart className="text-primary" />按公司</CardTitle>
            <CardDescription>公司维度分布</CardDescription>
          </CardHeader>
          <CardContent>
            <ChartCanvas type="pie" title="按公司" labels={(stats?.charts?.byCompany || []).map(x => x.name)} data={(stats?.charts?.byCompany || []).map(x => x.value)} colors={CATEGORY_COLORS} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart className="text-primary" />按行业</CardTitle>
            <CardDescription>行业维度分布</CardDescription>
          </CardHeader>
          <CardContent>
            <ChartCanvas type="bar" title="按行业" labels={(stats?.charts?.byIndustry || []).map(x => x.name)} data={(stats?.charts?.byIndustry || []).map(x => x.value)} colors={CATEGORY_COLORS} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiLineChart className="text-primary" />经验趋势</CardTitle>
            <CardDescription>不同经验要求的岗位量（折线）</CardDescription>
          </CardHeader>
          <CardContent>
            {(() => {
              const arr = (stats?.charts?.byExperience && stats.charts.byExperience.length > 0)
                ? stats.charts.byExperience
                : fallbackByExperience
              return <ChartCanvas type="line" title="经验趋势" labels={arr.map(x => x.name)} data={arr.map(x => x.value)} color="#3b82f6" />
            })()}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart className="text-primary" />薪资分布</CardTitle>
            <CardDescription>中位数K的分布桶</CardDescription>
          </CardHeader>
          <CardContent>
            {(() => {
              const apiBuckets = stats?.charts?.salaryBuckets || []
              const apiSum = apiBuckets.reduce((a, b) => a + (b?.value || 0), 0)
              const buckets = (apiBuckets.length > 0 && apiSum > 0)
                ? apiBuckets
                : (computedSalaryBuckets.length ? computedSalaryBuckets : fallbackSalaryBuckets)
              const labels = buckets.map(x => x.bucket)
              const data = buckets.map(x => x.value)
              return <ChartCanvas type="bar" title="薪资分布" labels={labels} data={data} colors={CATEGORY_COLORS} />
            })()}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiPieChart className="text-primary" />按状态</CardTitle>
            <CardDescription>已投递与未投递占比</CardDescription>
          </CardHeader>
          <CardContent>
            <ChartCanvas type="pie" title="按状态" labels={(stats?.charts?.byStatus || []).map(x => x.name)} data={(stats?.charts?.byStatus || []).map(x => x.value)} colors={["#10b981", "#64748b"]} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart className="text-primary" />按经验</CardTitle>
            <CardDescription>经验要求的分布</CardDescription>
          </CardHeader>
          <CardContent>
            {(() => {
              const arr = (stats?.charts?.byExperience && stats.charts.byExperience.length > 0)
                ? stats.charts.byExperience
                : fallbackByExperience
              return <ChartCanvas type="bar" title="按经验" labels={arr.map(x => x.name)} data={arr.map(x => x.value)} colors={CATEGORY_COLORS} />
            })()}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart className="text-primary" />按学历</CardTitle>
            <CardDescription>学历要求的分布</CardDescription>
          </CardHeader>
          <CardContent>
            {(() => {
              const arr = (stats?.charts?.byDegree && stats.charts.byDegree.length > 0)
                ? stats.charts.byDegree
                : fallbackByDegree
              return <ChartCanvas type="bar" title="按学历" labels={arr.map(x => x.name)} data={arr.map(x => x.value)} colors={CATEGORY_COLORS} />
            })()}
          </CardContent>
        </Card>
        {/* 已按需求移除 HR 活跃度图表卡片 */}
      </div>

      {/* 列表 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">岗位列表</CardTitle>
          <CardDescription>按筛选条件展示猎聘岗位数据</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto max-h-[560px] overflow-y-auto">
            <table className="min-w-full text-sm table-fixed">
              <thead>
                <tr className="text-left">
                  <th className="py-2 px-3 w-64">公司</th>
                  <th className="py-2 px-3 w-[28rem]">岗位</th>
                  <th className="py-2 px-3">薪资</th>
                  <th className="py-2 px-3">城市</th>
                  <th className="py-2 px-3">经验</th>
                  <th className="py-2 px-3">学历</th>
                  <th className="py-2 px-3">HR</th>
                  <th className="py-2 px-3">状态</th>
                  <th className="py-2 px-3">链接</th>
                  <th className="py-2 px-3">创建时间</th>
                </tr>
              </thead>
              <tbody>
                {(items || []).map((it) => (
                  <tr key={it.jobId} className="border-t border-white/10">
                    <td className="py-2 px-3 whitespace-nowrap">
                      <div className="font-medium max-w-[16rem] truncate" title={it.compName || ""}>{it.compName || ""}</div>
                      <div className="text-xs text-muted-foreground">{it.compIndustry || ""}</div>
                    </td>
                    <td className="py-2 px-3">
                      <button className="text-left w-full">
                        <div className="font-medium max-w-[28rem] truncate" title={it.jobTitle || ""} onClick={() => setDetailJob(it)}>{it.jobTitle || ""}</div>
                      </button>
                    </td>
                    <td className="py-2 px-3 whitespace-nowrap">{it.jobSalaryText || ""}</td>
                    <td className="py-2 px-3 whitespace-nowrap">{it.jobArea || ""}</td>
                    <td className="py-2 px-3 whitespace-nowrap">{it.jobExpReq || ""}</td>
                    <td className="py-2 px-3 whitespace-nowrap">{it.jobEduReq || ""}</td>
                    <td className="py-2 px-3 whitespace-nowrap">{it.hrName || ""}</td>
                    <td className="py-2 px-3 whitespace-nowrap">
                      <span className={badgeClass("delivery", it.delivered === 1 ? "已投递" : "未投递")}>{it.delivered === 1 ? "已投递" : "未投递"}</span>
                    </td>
                    <td className="py-2 px-3 whitespace-nowrap">
                      {it.jobLink ? (
                        <a href={it.jobLink} target="_blank" rel="noreferrer" className="text-primary hover:underline">打开</a>
                      ) : (
                        <span className="text-muted-foreground">-</span>
                      )}
                    </td>
                    <td className="py-2 px-3 whitespace-nowrap">{formatDateOnly(it.createTime)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 详情弹层 */}
          {typeof detailJob !== "undefined" && detailJob && (
            <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50" onClick={() => setDetailJob(null)}>
              <div className="bg-white dark:bg-neutral-900 rounded-lg shadow-xl w-[800px] max-w-[90vw] p-6" onClick={(e) => e.stopPropagation()}>
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-lg font-semibold">岗位详情</h3>
                  <Button className="rounded-full bg-white/10" onClick={() => setDetailJob(null)}>关闭</Button>
                </div>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div><span className="text-muted-foreground">公司：</span>{detailJob.compName || ""}</div>
                  <div><span className="text-muted-foreground">行业：</span>{detailJob.compIndustry || ""}</div>
                  <div><span className="text-muted-foreground">岗位：</span>{detailJob.jobTitle || ""}</div>
                  <div><span className="text-muted-foreground">薪资：</span>{detailJob.jobSalaryText || ""}</div>
                  <div><span className="text-muted-foreground">城市：</span>{detailJob.jobArea || ""}</div>
                  <div><span className="text-muted-foreground">经验：</span>{detailJob.jobExpReq || ""}</div>
                  <div><span className="text-muted-foreground">学历：</span>{detailJob.jobEduReq || ""}</div>
                  <div><span className="text-muted-foreground">HR：</span>{detailJob.hrName || ""}</div>
                  <div><span className="text-muted-foreground">状态：</span>{detailJob.delivered === 1 ? "已投递" : "未投递"}</div>
                  <div><span className="text-muted-foreground">创建时间：</span>{formatDateOnly(detailJob.createTime)}</div>
                </div>
                <div className="mt-4">
                  {detailJob.jobLink ? (
                    <a href={detailJob.jobLink} target="_blank" rel="noreferrer" className="text-primary hover:underline">打开职位链接</a>
                  ) : (
                    <span className="text-muted-foreground">暂无链接</span>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* 分页 */}
          <div className="mt-4 flex items-center gap-2">
            <Label className="text-sm">页码</Label>
            <Input
              value={inputPage}
              onChange={(e) => setInputPage(e.target.value)}
              onBlur={() => {
                const v = Number(inputPage)
                if (!isNaN(v) && v > 0) { setPage(v); loadList(v, size) }
                else setInputPage(page)
              }}
              className="w-20"
            />
            <Label className="text-sm">每页</Label>
            <Input
              value={inputSize}
              onChange={(e) => setInputSize(e.target.value)}
              onBlur={() => {
                const v = Number(inputSize)
                if (!isNaN(v) && v > 0) { setSize(v); loadList(page, v) }
                else setInputSize(size)
              }}
              className="w-20"
            />
            <span className="text-sm text-muted-foreground">共 {total} 条</span>
            <div className="ml-auto flex items-center gap-2">
              <Button onClick={() => loadList(page - 1 > 0 ? page - 1 : 1, size)} disabled={loadingList || page <= 1} className="rounded-full bg-white/10">
                上一页
              </Button>
              <Button onClick={() => loadList(page + 1, size)} disabled={loadingList || items.length < size} className="rounded-full bg-white/10">
                下一页
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}