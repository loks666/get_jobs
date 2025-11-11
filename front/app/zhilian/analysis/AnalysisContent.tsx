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
    byCompany: NameValue[]
    byExperience: NameValue[]
    byDegree: NameValue[]
    salaryBuckets: BucketValue[]
    dailyTrend?: NameValue[]
  }
}

type ZhilianJob = {
  jobId: string
  companyName?: string
  jobTitle?: string
  salary?: string
  location?: string
  experience?: string
  degree?: string
  deliveryStatus?: string
  jobLink?: string
  createTime?: string
}

type PagedResult = {
  items: ZhilianJob[]
  total: number
  page: number
  size: number
}

const API_BASE = process.env.API_BASE_URL || "http://localhost:8888"

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

function formatDateOnly(s?: string) {
  if (!s) return ""
  try {
    const d = new Date(s)
    if (isNaN(d.getTime())) return s
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
  } catch (e) {
    return s
  }
}

function badgeClass(type: "status" | "delivery", text?: string) {
  const base = "px-2 py-0.5 rounded text-xs"
  if (type === "delivery") {
    if (!text || text === "未投递") return `${base} bg-yellow-100 text-yellow-800 dark:bg-yellow-900/40 dark:text-yellow-200`
    if (text === "已投递") return `${base} bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-200`
    if (text === "已过滤") return `${base} bg-gray-100 text-gray-800 dark:bg-gray-900/40 dark:text-gray-200`
    if (text === "投递失败") return `${base} bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-200`
    return `${base} bg-gray-100 text-gray-800`
  }
  return base
}

export default function AnalysisContent({ showHeader = false }: { showHeader?: boolean }) {
  const [stats, setStats] = useState<StatsResponse | null>(null)
  const [loadingStats, setLoadingStats] = useState(true)

  const [items, setItems] = useState<ZhilianJob[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [inputPage, setInputPage] = useState<number | string>(1)
  const [inputSize, setInputSize] = useState<number | string>(20)

  const [statuses, setStatuses] = useState<string[]>([])
  const [location, setLocation] = useState<string>("")
  const [experience, setExperience] = useState<string>("")
  const [degree, setDegree] = useState<string>("")
  const [minK, setMinK] = useState<number | string>("")
  const [maxK, setMaxK] = useState<number | string>("")
  const [keyword, setKeyword] = useState<string>("")

  const [exporting, setExporting] = useState(false)
  const [reloading, setReloading] = useState(false)
  const [computedSalaryBuckets, setComputedSalaryBuckets] = useState<BucketValue[]>([])

  const statusOptions = ["未投递", "已投递", "已过滤", "投递失败"]

  const loadList = async (toPage = page, toSize = size) => {
    try {
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
      const res = await fetch(`${API_BASE}/api/zhilian/list?${params.toString()}`)
      const data: PagedResult = await res.json()
      setItems(data.items || [])
      setTotal(data.total || 0)
      setPage(data.page || toPage)
      setSize(data.size || toSize)
    } catch (e) {
      console.error("fetch zhilian list failed", e)
    }
  }

  const loadStats = async () => {
    try {
      setLoadingStats(true)
      const params = new URLSearchParams()
      if (statuses.length) params.set("statuses", statuses.join(","))
      if (location) params.set("location", location)
      if (experience) params.set("experience", experience)
      if (degree) params.set("degree", degree)
      if (minK) params.set("minK", String(Number(minK)))
      if (maxK) params.set("maxK", String(Number(maxK)))
      if (keyword) params.set("keyword", keyword)
      const res = await fetch(`${API_BASE}/api/zhilian/stats?${params.toString()}`)
      const data: StatsResponse = await res.json()
      setStats(data)
    } catch (e) {
      console.error("fetch zhilian stats failed", e)
    } finally {
      setLoadingStats(false)
    }
  }

  useEffect(() => {
    loadList(1, size)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    loadStats()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statuses.join(","), location, experience, degree, minK, maxK, keyword])

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
      let all: ZhilianJob[] = []
      let totalCount = 0

      while (true) {
        const params = new URLSearchParams(baseParams)
        params.set("page", String(currentPage))
        params.set("size", String(pageSize))
        const res = await fetch(`${API_BASE}/api/zhilian/list?${params.toString()}`)
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
        "投递状态",
        "链接",
        "创建时间",
      ]
      const rows = all.map((it) => [
        it.companyName || "",
        it.jobTitle || "",
        it.salary || "",
        it.location || "",
        it.experience || "",
        it.degree || "",
        it.deliveryStatus || "",
        it.jobLink || "",
        formatDateOnly(it.createTime),
      ])

      const csv = [header.join(","), ...rows.map((r) => r.map((v) => String(v).replace(/"/g, '""')).join(","))].join("\n")
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" })
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `zhilian_jobs_${Date.now()}.csv`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    } catch (e) {
      console.error("export csv failed", e)
    } finally {
      setExporting(false)
    }
  }

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
        const res = await fetch(`${API_BASE}/api/zhilian/list?${params.toString()}`)
        const data: PagedResult = await res.json()
        const chunk = data.items || []
        if (currentPage === 1) totalCount = data.total || chunk.length
        for (const it of chunk) {
          const info = parseSalary(it.salary)
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
      const counts = buckets.map((b) => ks.filter((k) => (b.max == null ? k >= b.min : k >= b.min && k < b.max)).length)
      setComputedSalaryBuckets(buckets.map((b, i) => ({ bucket: b.key, value: counts[i] })))
    } catch (e) {
      console.error("compute salary buckets failed", e)
      setComputedSalaryBuckets([])
    }
  }

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

  const kpiCards = useMemo(() => {
    const k = stats?.kpi
    const avgMonthlyKFromItems = (() => {
      if (!items?.length) return undefined
      const ks: number[] = []
      for (const it of items) {
        const info = parseSalary(it.salary)
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

  const fallbackSalaryBuckets = useMemo(() => {
    const ks: number[] = []
    for (const it of items) {
      const info = parseSalary(it.salary)
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
    const counts = buckets.map((b) => ks.filter((k) => (b.max == null ? k >= b.min : k >= b.min && k < b.max)).length)
    return buckets.map((b, i) => ({ bucket: b.key, value: counts[i] }))
  }, [items])

  return (
    <div className="space-y-8">
      {showHeader && (
        <PageHeader title="智联 投递分析" subtitle="基于 zhilian_data 表的统计图与列表分析" icon={<BiBarChart size={28} />} />
      )}

      {/* KPI 卡片 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {kpiCards.map((c, idx) => (
          <Card key={idx} className="border">
            <CardHeader>
              <CardTitle className="text-sm">{c.title}</CardTitle>
              <CardDescription className="text-xl font-semibold">{c.value}</CardDescription>
            </CardHeader>
          </Card>
        ))}
      </div>

      {/* 操作栏 */}
      <Card>
        <CardHeader className="space-y-0">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <CardTitle className="text-base">筛选与操作</CardTitle>
              <CardDescription>按状态、地区、经验、学历与薪资区间过滤列表</CardDescription>
            </div>
            <div className="flex flex-wrap gap-3 rounded-full px-3 py-2 border border-white/20 bg-white/5 backdrop-blur-md shadow-sm">
              {statusOptions.map((s) => (
                <button
                  key={s}
                  onClick={() => setStatuses((prev) => (prev.includes(s) ? prev.filter((x) => x !== s) : [...prev, s]))}
                  className={`px-3 py-1.5 rounded-full text-xs border ${statuses.includes(s) ? "bg-primary text-white border-primary" : "bg-transparent text-primary border-primary"}`}
                >
                  {s}
                </button>
              ))}
              <button className="px-3 py-1.5 rounded-full text-xs border" onClick={() => setStatuses([])}>重置</button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-6 gap-4">
            <div className="space-y-2">
              <Label>地区</Label>
              <Input placeholder="如：北京" value={location} onChange={(e) => setLocation(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>经验</Label>
              <Input placeholder="如：3-5年" value={experience} onChange={(e) => setExperience(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>学历</Label>
              <Input placeholder="如：本科" value={degree} onChange={(e) => setDegree(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>最低K</Label>
              <Input placeholder="如：10" value={minK} onChange={(e) => setMinK(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>最高K</Label>
              <Input placeholder="如：30" value={maxK} onChange={(e) => setMaxK(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>关键词</Label>
              <Input placeholder="公司或岗位关键词" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
            </div>
          </div>

          <div className="mt-4 flex items-center gap-3">
            <Button variant="default" onClick={() => loadStats()} disabled={loadingStats}>
              <BiRefresh className="mr-1" /> 刷新统计
            </Button>
            <Button variant="outline" onClick={() => loadList(1, size)}>
              <BiBriefcase className="mr-1" /> 刷新列表
            </Button>
            <Button variant="outline" onClick={exportCSV} disabled={exporting}>
              <BiDownload className="mr-1" /> 导出CSV
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 图表区 */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart /> 投递状态分布</CardTitle>
            <CardDescription>按 delivery_status 聚合</CardDescription>
          </CardHeader>
          <CardContent>
            {stats ? (
              <ChartCanvas type="pie" labels={stats.charts.byStatus.map((x) => x.name)} data={stats.charts.byStatus.map((x) => x.value)} colors={CATEGORY_COLORS} />
            ) : (
              <div className="text-muted-foreground">加载中...</div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart /> 城市TOP10</CardTitle>
            <CardDescription>按地区聚合</CardDescription>
          </CardHeader>
          <CardContent>
            {stats ? (
              <ChartCanvas type="bar" labels={stats.charts.byCity.map((x) => x.name)} data={stats.charts.byCity.map((x) => x.value)} color="#3b82f6" />
            ) : (
              <div className="text-muted-foreground">加载中...</div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart /> 公司岗位数TOP10</CardTitle>
            <CardDescription>按公司名称聚合</CardDescription>
          </CardHeader>
          <CardContent>
            {stats ? (
              <ChartCanvas type="bar" labels={stats.charts.byCompany.map((x) => x.name)} data={stats.charts.byCompany.map((x) => x.value)} color="#10b981" />
            ) : (
              <div className="text-muted-foreground">加载中...</div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart /> 经验分布</CardTitle>
            <CardDescription>不同经验要求的岗位数</CardDescription>
          </CardHeader>
          <CardContent>
            {stats ? (
              <ChartCanvas type="bar" labels={stats.charts.byExperience.map((x) => x.name)} data={stats.charts.byExperience.map((x) => x.value)} color="#f59e0b" />
            ) : (
              <div className="text-muted-foreground">加载中...</div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart /> 学历分布</CardTitle>
            <CardDescription>不同学历要求的岗位数</CardDescription>
          </CardHeader>
          <CardContent>
            {stats ? (
              <ChartCanvas type="bar" labels={stats.charts.byDegree.map((x) => x.name)} data={stats.charts.byDegree.map((x) => x.value)} color="#6366f1" />
            ) : (
              <div className="text-muted-foreground">加载中...</div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiLineChart /> 薪资区间分布</CardTitle>
            <CardDescription>基于中位数K的桶聚合（后端或前端计算）</CardDescription>
          </CardHeader>
          <CardContent>
            {stats ? (
              <ChartCanvas type="line" labels={(computedSalaryBuckets.length ? computedSalaryBuckets : stats.charts.salaryBuckets).map((x) => (x as any).bucket)} data={(computedSalaryBuckets.length ? computedSalaryBuckets : stats.charts.salaryBuckets).map((x) => x.value)} color="#ef4444" />
            ) : (
              <div className="text-muted-foreground">加载中...</div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* 列表区 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">岗位列表</CardTitle>
          <CardDescription>分页展示符合筛选条件的岗位</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="bg-muted">
                  <th className="py-2 px-3 text-left">公司</th>
                  <th className="py-2 px-3 text-left">岗位</th>
                  <th className="py-2 px-3 text-left">薪资</th>
                  <th className="py-2 px-3 text-left">地点</th>
                  <th className="py-2 px-3 text-left">经验</th>
                  <th className="py-2 px-3 text-left">学历</th>
                  <th className="py-2 px-3 text-left">投递状态</th>
                  <th className="py-2 px-3 text-left">链接</th>
                  <th className="py-2 px-3 text-left">创建时间</th>
                </tr>
              </thead>
              <tbody>
                {items.map((it, idx) => (
                  <tr key={`${it.jobId}-${idx}`} className="border-t">
                    <td className="py-2 px-3 whitespace-nowrap">{it.companyName || ""}</td>
                    <td className="py-2 px-3 whitespace-nowrap">{it.jobTitle || ""}</td>
                    <td className="py-2 px-3 whitespace-nowrap">{it.salary || ""}</td>
                    <td className="py-2 px-3 whitespace-nowrap">{it.location || ""}</td>
                    <td className="py-2 px-3 whitespace-nowrap">{it.experience || ""}</td>
                    <td className="py-2 px-3 whitespace-nowrap">{it.degree || ""}</td>
                    <td className="py-2 px-3 whitespace-nowrap">
                      <span className={badgeClass("delivery", it.deliveryStatus)}>{it.deliveryStatus || ""}</span>
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

          {/* 分页 */}
          <div className="mt-4 flex items-center gap-2">
            <Label className="text-sm">页码</Label>
            <Input
              className="w-20"
              value={inputPage}
              onChange={(e) => setInputPage(e.target.value)}
              onBlur={() => {
                const p = Number(inputPage)
                const s = Number(size)
                if (!isNaN(p) && p > 0) loadList(p, s)
              }}
            />
            <Label className="text-sm">每页条数</Label>
            <Input
              className="w-24"
              value={inputSize}
              onChange={(e) => setInputSize(e.target.value)}
              onBlur={() => {
                const p = Number(page)
                const s = Number(inputSize)
                if (!isNaN(s) && s > 0) loadList(p, s)
              }}
            />
            <Button variant="outline" onClick={() => loadList(Number(page), Number(size))}>
              跳转
            </Button>
            <div className="text-sm text-muted-foreground">共 {total} 条</div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}