"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select } from "@/components/ui/select"
import { Label } from "@/components/ui/label"
import PageHeader from "@/app/components/PageHeader"
import { BiRefresh, BiDownload, BiBarChart, BiLineChart, BiPieChart, BiBriefcase } from "react-icons/bi"

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

type BossJob = {
  id: number
  companyName?: string
  jobName?: string
  salary?: string
  location?: string
  experience?: string
  degree?: string
  hrName?: string
  hrPosition?: string
  hrActiveStatus?: string
  deliveryStatus?: string
  jobUrl?: string
  recruitmentStatus?: string
  companyAddress?: string
  industry?: string
  introduce?: string
  financingStage?: string
  companyScale?: string
  jobDescription?: string
  createdAt?: string
}

type PagedResult = {
  items: BossJob[]
  total: number
  page: number
  size: number
}

const API_BASE = "http://localhost:8888"
// 通用分类颜色（用于柱状/饼状图每个分类不同颜色）
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
  // 颜色统一使用纯色（不透明）
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

    // 销毁旧图表
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
        // 柱状图每个分类使用纯色
        return colors.slice(0, data.length).map((c) => toSolid(c))
      }
      // 折线图/默认均使用纯色
      return toSolid(color ?? "#3b82f6")
    })()

    const borderColor = (() => {
      if (type === "pie") {
        // 饼图无需边框或统一边框
        return undefined
      }
      if (type === "bar" && colors && colors.length) {
        return colors.slice(0, data.length)
      }
      return color
    })()

    const dataset: any = {
      label: title || "",
      data,
      backgroundColor,
      borderColor,
    }

    // 线形图不使用区域填充，点与线均为纯色
    if (type === "line") {
      dataset.fill = false
      dataset.pointBackgroundColor = toSolid(color)
      dataset.pointBorderColor = toSolid(color)
    }

    ;(async () => {
      try {
        const Chart = await ensureChart()
        // 检查组件是否已卸载
        if (cancelled) return

        chartRef.current = new Chart(ctx, {
          type,
          data: {
            labels,
            datasets: [dataset],
          },
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

  const [items, setItems] = useState<BossJob[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  // 分页输入（页码与每页条数），便于自定义跳转与控制
  const [inputPage, setInputPage] = useState<number | string>(1)
  const [inputSize, setInputSize] = useState<number | string>(20)

  const [statuses, setStatuses] = useState<string[]>([]) // 默认不勾选任何状态
  const [location, setLocation] = useState<string>("")
  const [experience, setExperience] = useState<string>("")
  const [degree, setDegree] = useState<string>("")
  const [minK, setMinK] = useState<string>("")
  const [maxK, setMaxK] = useState<string>("")
  const [keyword, setKeyword] = useState<string>("")
  const [loadingList, setLoadingList] = useState(false)
  const [reloading, setReloading] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [filterHeadhunter, setFilterHeadhunter] = useState<boolean>(false)

  // 查看全文弹窗
  const [showTextDialog, setShowTextDialog] = useState(false)
  const [textDialogTitle, setTextDialogTitle] = useState<string>("")
  const [textDialogContent, setTextDialogContent] = useState<string>("")
  const textAreaRef = useRef<HTMLTextAreaElement | null>(null)

  const openTextDialog = (title: string, content?: string) => {
    setTextDialogTitle(title)
    setTextDialogContent(content || "")
    setShowTextDialog(true)
  }

  const selectDialogText = () => {
    const el = textAreaRef.current
    if (el) el.select()
  }

  const copyDialogText = async () => {
    try {
      await navigator.clipboard.writeText(textDialogContent || "")
      alert("已复制到剪贴板")
    } catch (e) {
      try {
        const ta = document.createElement("textarea")
        ta.value = textDialogContent || ""
        document.body.appendChild(ta)
        ta.select()
        document.execCommand("copy")
        document.body.removeChild(ta)
        alert("已复制到剪贴板")
      } catch (e2) {
        alert("复制失败，请手动选中复制")
      }
    }
  }

  const statusOptions = ["未投递", "已投递", "已过滤", "投递失败"]

  useEffect(() => {
    // 初次加载统计（应用当前筛选条件）
    loadStats()
  }, [])

  // 当实际页码/每页条数变化时，同步到输入框
  useEffect(() => {
    setInputPage(page)
  }, [page])
  useEffect(() => {
    setInputSize(size)
  }, [size])

  // 勾选“过滤猎头岗位”后自动刷新列表（从第1页开始）
  useEffect(() => {
    loadList(1, size)
    loadStats()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterHeadhunter])

  // 仅显示日期（YYYY-MM-DD）
  const formatDateOnly = (s?: string) => {
    if (!s) return ""
    const d = new Date(s)
    if (!isNaN(d.getTime())) {
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, "0")
      const day = String(d.getDate()).padStart(2, "0")
      return `${y}-${m}-${day}`
    }
    // 非标准时间串，兜底截取前10位
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
    if (filterHeadhunter) params.set("filterHeadhunter", "true")
    params.set("page", String(toPage))
    params.set("size", String(toSize))

    try {
      setLoadingList(true)
      const res = await fetch(`${API_BASE}/api/boss/list?${params.toString()}`)
      const data: PagedResult = await res.json()
      // 前端兜底过滤猎头（避免后端未更新导致的显示异常）
      const filteredItems = (data.items || []).filter(it => {
        if (!filterHeadhunter) return true
        const hp = (it.hrPosition || "").toLowerCase()
        return !(hp.includes("猎头") || hp.includes("獵頭"))
      })
      setItems(filteredItems)
      setTotal(data.total || 0)
      setPage(data.page || toPage)
      setSize(data.size || toSize)
    } catch (e) {
      console.error("fetch list failed", e)
    } finally {
      setLoadingList(false)
    }
  }

  // 统计图加载：与列表共享相同筛选条件
  const loadStats = async () => {
    const params = new URLSearchParams()
    if (statuses.length) params.set("statuses", statuses.join(","))
    if (location) params.set("location", location)
    if (experience) params.set("experience", experience)
    if (degree) params.set("degree", degree)
    if (minK) params.set("minK", String(Number(minK)))
    if (maxK) params.set("maxK", String(Number(maxK)))
    if (keyword) params.set("keyword", keyword)
    if (filterHeadhunter) params.set("filterHeadhunter", "true")

    try {
      setLoadingStats(true)
      const res = await fetch(`${API_BASE}/api/boss/stats?${params.toString()}`)
      const data: StatsResponse = await res.json()
      setStats(data)
    } catch (e) {
      console.error("fetch stats failed", e)
    } finally {
      setLoadingStats(false)
    }
  }

  useEffect(() => {
    loadList(1, size)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onReload = async () => {
    try {
      setReloading(true)
      const res = await fetch(`${API_BASE}/api/boss/reload`)
      const data = await res.json()
      console.log("reload", data)
      await loadList(1, size)
      await loadStats()
    } catch (e) {
      console.error("reload failed", e)
    } finally {
      setReloading(false)
    }
  }

  const exportCSV = async () => {
    try {
      setExporting(true)
      // 组装当前筛选条件
      const baseParams = new URLSearchParams()
      if (statuses.length) baseParams.set("statuses", statuses.join(","))
      if (location) baseParams.set("location", location)
      if (experience) baseParams.set("experience", experience)
      if (degree) baseParams.set("degree", degree)
      if (minK) baseParams.set("minK", String(Number(minK)))
      if (maxK) baseParams.set("maxK", String(Number(maxK)))
      if (keyword) baseParams.set("keyword", keyword)
      if (filterHeadhunter) baseParams.set("filterHeadhunter", "true")

      // 分页抓取，直到获取全部数据
      const pageSize = 1000
      let currentPage = 1
      let all: BossJob[] = []
      let totalCount = 0

      while (true) {
        const params = new URLSearchParams(baseParams)
        params.set("page", String(currentPage))
        params.set("size", String(pageSize))
        const res = await fetch(`${API_BASE}/api/boss/list?${params.toString()}`)
        const data: PagedResult = await res.json()
        let chunk = data.items || []
        // 导出也做兜底过滤，确保CSV不含猎头岗位
        if (filterHeadhunter) {
          chunk = chunk.filter(it => {
            const hp = (it.hrPosition || "").toLowerCase()
            return !(hp.includes("猎头") || hp.includes("獵頭"))
          })
        }
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
        it.companyName || "",
        it.jobName || "",
        it.salary || "",
        it.location || "",
        it.experience || "",
        it.degree || "",
        it.hrName || "",
        it.deliveryStatus || "",
        it.jobUrl || "",
        it.createdAt || "",
      ])
      const csv = [header, ...rows]
        .map((r) => r.map((v) => (String(v).includes(",") ? `"${String(v).replace(/"/g, '""')}"` : String(v))).join(","))
        .join("\n")
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" })
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `boss_jobs_${new Date().toISOString().slice(0, 10)}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      console.error("export CSV failed", e)
      alert("导出失败，请稍后重试")
    } finally {
      setExporting(false)
    }
  }

  // 彩色标签样式（用于状态类字段）
  const badgeClass = (kind: "delivery" | "hr" | "recruitment", value?: string) => {
    const base = "px-2 py-1 rounded-full text-xs font-medium whitespace-nowrap"
    const v = (value || "").trim()
    if (kind === "delivery") {
      if (v.includes("已投递")) return `${base} bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300`
      if (v.includes("已过滤")) return `${base} bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-300`
      if (v.includes("失败")) return `${base} bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300`
      return `${base} bg-slate-100 text-slate-700 dark:bg-slate-800/50 dark:text-slate-300`
    }
    if (kind === "hr") {
      if (/刚|在线|今日/.test(v)) return `${base} bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300`
      if (/小时|近/.test(v)) return `${base} bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300`
      if (/天|周|月|很久/.test(v)) return `${base} bg-slate-100 text-slate-700 dark:bg-slate-800/50 dark:text-slate-300`
      return `${base} bg-slate-100 text-slate-700 dark:bg-slate-800/50 dark:text-slate-300`
    }
    // recruitment
    if (/暂停|关闭|下线|结束/.test(v)) return `${base} bg-gray-200 text-gray-800 dark:bg-gray-700/60 dark:text-gray-200`
    if (/急/.test(v)) return `${base} bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300`
    if (/招|招聘|中/.test(v)) return `${base} bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300`
    return `${base} bg-gray-100 text-gray-700 dark:bg-gray-700/50 dark:text-gray-200`
  }

  const kpiCards = useMemo(() => {
    const k = stats?.kpi
    return [
      { title: "总岗位数", value: k?.total ?? 0 },
      { title: "已投递", value: k?.delivered ?? 0 },
      { title: "未投递", value: k?.pending ?? 0 },
      { title: "已过滤", value: k?.filtered ?? 0 },
      { title: "投递失败", value: k?.failed ?? 0 },
      { title: "平均月薪(K)", value: k?.avgMonthlyK ?? 0 },
    ]
  }, [stats])

  return (
    <div className="space-y-8">
      {showHeader && (
        <PageHeader
          title="Boss 投递分析"
          subtitle="基于 boss_data 表的统计图与列表分析"
          icon={<BiBarChart size={28} />}
        />
      )}

      {/* KPI 卡片 */}
      <div className="grid grid-cols-2 md:grid-cols-6 gap-4">
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
                <label
                  key={s}
                  className={`group inline-flex items-center gap-2 text-sm rounded-full px-3 py-1.5 transition-all border backdrop-blur-sm ${
                    statuses.includes(s)
                      ? "border-cyan-300/60 bg-gradient-to-r from-cyan-500/15 to-violet-500/15 text-cyan-900 dark:text-cyan-200 shadow"
                      : "border-white/20 bg-white/8 text-foreground/80 hover:bg-white/12"
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={statuses.includes(s)}
                    onChange={(e) => {
                      setStatuses((prev) => {
                        if (e.target.checked) return Array.from(new Set([...prev, s]))
                        return prev.filter((x) => x !== s)
                      })
                    }}
                    className="sr-only peer"
                  />
                  <span className="inline-flex h-4 w-4 items-center justify-center rounded-md border border-white/30 bg-white/10 shadow-inner transition-all peer-checked:bg-cyan-400/60 peer-checked:border-cyan-300/80"></span>
                  {s}
                </label>
              ))}
              <label
                className={`group inline-flex items-center gap-2 text-sm rounded-full px-3 py-1.5 transition-all border backdrop-blur-sm ${
                  filterHeadhunter
                    ? "border-teal-300/60 bg-gradient-to-r from-teal-500/15 to-emerald-500/15 text-teal-900 dark:text-teal-200 shadow"
                    : "border-white/20 bg-white/8 text-foreground/80 hover:bg-white/12"
                }`}
              >
                <input
                  type="checkbox"
                  checked={filterHeadhunter}
                  onChange={(e) => setFilterHeadhunter(e.target.checked)}
                  className="sr-only peer"
                />
                <span className="inline-flex h-4 w-4 items-center justify-center rounded-md border border-white/30 bg-white/10 shadow-inner transition-all peer-checked:bg-teal-400/60 peer-checked:border-teal-300/80"></span>
                过滤猎头岗位
              </label>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-6 gap-4">
            <div>
              <Label>城市</Label>
              <Input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="如：深圳" />
            </div>
            <div>
              <Label>经验</Label>
              <Input value={experience} onChange={(e) => setExperience(e.target.value)} placeholder="如：3-5年" />
            </div>
            <div>
              <Label>学历</Label>
              <Input value={degree} onChange={(e) => setDegree(e.target.value)} placeholder="如：本科" />
            </div>
            <div>
              <Label>最低月薪(K)</Label>
              <Input type="number" value={minK} onChange={(e) => setMinK(e.target.value)} placeholder="10" />
            </div>
            <div>
              <Label>最高月薪(K)</Label>
              <Input type="number" value={maxK} onChange={(e) => setMaxK(e.target.value)} placeholder="30" />
            </div>
            <div>
              <Label>关键词</Label>
              <Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="公司/岗位/HR" />
            </div>
          </div>

          <div className="mt-4 flex gap-3">
            <Button
              onClick={async () => {
                await loadList(1, size)
                await loadStats()
              }}
              disabled={loadingList}
            >
              <BiBarChart className="mr-2" /> 应用筛选
            </Button>
            <Button variant="success" onClick={exportCSV} disabled={exporting}>
              <BiDownload className="mr-2" /> {exporting ? "导出中..." : "导出CSV"}
            </Button>
            <Button variant="outline" onClick={onReload} disabled={reloading}>
              <BiRefresh className="mr-2" /> 刷新数据
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 图表区：6个图表（已移除每日趋势与HR活跃度） */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiPieChart /> 投递状态分布</CardTitle>
            <CardDescription>已投递/未投递/已过滤/失败等占比</CardDescription>
          </CardHeader>
          <CardContent>
            {stats ? (
              <ChartCanvas
                type="pie"
                labels={stats.charts.byStatus.map((x) => x.name)}
                data={stats.charts.byStatus.map((x) => x.value)}
              />
            ) : (
              <div className="h-64 flex items-center justify-center border-2 border-dashed rounded-lg text-muted-foreground">
                加载中...
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart /> 行业TOP10</CardTitle>
            <CardDescription>岗位按行业聚合</CardDescription>
          </CardHeader>
          <CardContent>
            {stats ? (
              <ChartCanvas
                type="bar"
                labels={stats.charts.byIndustry.map((x) => x.name)}
                data={stats.charts.byIndustry.map((x) => x.value)}
                colors={CATEGORY_COLORS}
              />
            ) : (
              <div className="h-64 flex items-center justify-center border-2 border-dashed rounded-lg text-muted-foreground">加载中...</div>
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
              <ChartCanvas type="bar" labels={stats.charts.byCompany.map((x) => x.name)} data={stats.charts.byCompany.map((x) => x.value)} colors={CATEGORY_COLORS} />
            ) : (
              <div className="h-64 flex items-center justify-center border-2 border-dashed rounded-lg text-muted-foreground">加载中...</div>
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
              <ChartCanvas type="bar" labels={stats.charts.byExperience.map((x) => x.name)} data={stats.charts.byExperience.map((x) => x.value)} colors={CATEGORY_COLORS} />
            ) : (
              <div className="h-64 flex items-center justify-center border-2 border-dashed rounded-lg text-muted-foreground">加载中...</div>
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
              <ChartCanvas type="bar" labels={stats.charts.byDegree.map((x) => x.name)} data={stats.charts.byDegree.map((x) => x.value)} colors={CATEGORY_COLORS} />
            ) : (
              <div className="h-64 flex items-center justify-center border-2 border-dashed rounded-lg text-muted-foreground">加载中...</div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><BiLineChart /> 薪资区间分布</CardTitle>
            <CardDescription>基于中位数K的桶聚合</CardDescription>
          </CardHeader>
          <CardContent>
            {stats ? (
              <ChartCanvas type="line" labels={stats.charts.salaryBuckets.map((x) => x.bucket)} data={stats.charts.salaryBuckets.map((x) => x.value)} color="#ef4444" />
            ) : (
              <div className="h-64 flex items-center justify-center border-2 border-dashed rounded-lg text-muted-foreground">加载中...</div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* 列表 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2"><BiBriefcase /> 岗位数据</CardTitle>
          <CardDescription>支持筛选、导出与刷新</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto rounded-xl border border-stroke/30 dark:border-strokedark/30 shadow-lg">
            <table className="w-full table-fixed min-w-[1200px] bg-white dark:bg-blacksection">
              <thead>
                <tr className="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-950/30 dark:to-indigo-950/30 border-b-2 border-blue-200 dark:border-blue-800">
                  <th className="w-40 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">公司名称</th>
                  <th className="w-48 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">岗位名称</th>
                  <th className="w-24 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">薪资</th>
                  <th className="w-24 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">地点</th>
                  <th className="w-24 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">经验</th>
                  <th className="w-20 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">学历</th>
                  <th className="w-28 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">HR</th>
                  <th className="w-32 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">HR职位</th>
                  <th className="w-32 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">HR活跃</th>
                  <th className="w-24 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">投递状态</th>
                  <th className="w-24 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">招聘状态</th>
                  <th className="w-16 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">链接</th>
                  <th className="w-48 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">公司地址</th>
                  <th className="w-28 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">行业</th>
                  <th className="w-28 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">公司规模</th>
                  <th className="w-28 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">融资阶段</th>
                  <th className="w-48 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">公司介绍</th>
                  <th className="w-48 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200 border-r border-gray-200 dark:border-gray-700">岗位描述</th>
                  <th className="w-28 px-4 py-3.5 text-left text-sm font-semibold text-gray-700 dark:text-gray-200">创建时间</th>
                </tr>
              </thead>
              <tbody>
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={19} className="px-4 py-12 text-center text-muted-foreground bg-gray-50 dark:bg-gray-900/20">
                      <div className="flex flex-col items-center gap-3">
                        <BiBriefcase className="text-4xl text-gray-300 dark:text-gray-600" />
                        <p className="text-sm">暂无数据</p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  items.map((it, idx) => (
                    <tr
                      key={it.id}
                      className={`group transition-colors border-b border-gray-200 dark:border-gray-700 last:border-b-0 ${
                        idx % 2 === 0
                          ? 'bg-white dark:bg-blacksection hover:bg-blue-50/50 dark:hover:bg-blue-950/20'
                          : 'bg-gray-50/50 dark:bg-gray-900/20 hover:bg-blue-50/50 dark:hover:bg-blue-950/20'
                      }`}
                    >
                      <td className="px-4 py-3 text-sm leading-6 align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.companyName || '-'} onClick={() => openTextDialog("公司名称", it.companyName)}>{it.companyName || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.jobName || '-'} onClick={() => openTextDialog("岗位名称", it.jobName)}>{it.jobName || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 whitespace-nowrap align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.salary || '-'} onClick={() => openTextDialog("薪资", it.salary)}>{it.salary || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 whitespace-nowrap align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.location || '-'} onClick={() => openTextDialog("地点", it.location)}>{it.location || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 whitespace-nowrap align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.experience || '-'} onClick={() => openTextDialog("经验", it.experience)}>{it.experience || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 whitespace-nowrap align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.degree || '-'} onClick={() => openTextDialog("学历", it.degree)}>{it.degree || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.hrName || '-'} onClick={() => openTextDialog("HR", it.hrName)}>{it.hrName || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.hrPosition || '-'} onClick={() => openTextDialog("HR职位", it.hrPosition)}>{it.hrPosition || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 whitespace-nowrap align-top border-r border-gray-200 dark:border-gray-700">
                        <button className={badgeClass("hr", it.hrActiveStatus)} title={it.hrActiveStatus} onClick={() => openTextDialog("HR活跃", it.hrActiveStatus)}>{it.hrActiveStatus || "-"}</button>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 whitespace-nowrap align-top border-r border-gray-200 dark:border-gray-700">
                        <button className={badgeClass("delivery", it.deliveryStatus)} title={it.deliveryStatus} onClick={() => openTextDialog("投递状态", it.deliveryStatus)}>{it.deliveryStatus || "-"}</button>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 whitespace-nowrap align-top border-r border-gray-200 dark:border-gray-700">
                        <button className={badgeClass("recruitment", it.recruitmentStatus)} title={it.recruitmentStatus} onClick={() => openTextDialog("招聘状态", it.recruitmentStatus)}>{it.recruitmentStatus || "-"}</button>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 whitespace-nowrap align-top border-r border-gray-200 dark:border-gray-700">
                        {it.jobUrl ? (
                          <a href={it.jobUrl} className="text-primary underline hover:text-primary/80 transition-colors" target="_blank" rel="noreferrer">链接</a>
                        ) : (
                          "-"
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.companyAddress || '-'} onClick={() => openTextDialog("公司地址", it.companyAddress)}>{it.companyAddress || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.industry || '-'} onClick={() => openTextDialog("行业", it.industry)}>{it.industry || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.companyScale || '-'} onClick={() => openTextDialog("公司规模", it.companyScale)}>{it.companyScale || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.financingStage || '-'} onClick={() => openTextDialog("融资阶段", it.financingStage)}>{it.financingStage || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.introduce || '-'} onClick={() => openTextDialog("公司介绍", it.introduce)}>{it.introduce || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 align-top border-r border-gray-200 dark:border-gray-700">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={it.jobDescription || '-'} onClick={() => openTextDialog("岗位描述", it.jobDescription)}>{it.jobDescription || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm leading-6 whitespace-nowrap align-top">
                        <div className="truncate cursor-pointer hover:text-primary transition-colors" title={formatDateOnly(it.createdAt) || '-'} onClick={() => openTextDialog("创建时间", formatDateOnly(it.createdAt))}>{formatDateOnly(it.createdAt) || '-'}</div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex items-center gap-3">
            <Button variant="outline" onClick={() => loadList(Math.max(1, page - 1), size)} disabled={loadingList || page <= 1}>上一页</Button>
            <div className="text-sm">第 {page} 页 / 共 {Math.max(1, Math.ceil(total / size))} 页</div>
            <Button variant="outline" onClick={() => loadList(page + 1, size)} disabled={loadingList || page >= Math.ceil(total / size)}>下一页</Button>
            {/* 自定义页码与每页条数 */}
            <div className="flex items-center gap-2 ml-4">
              <Label className="text-sm">页码</Label>
              <Input
                type="number"
                value={inputPage}
                onChange={(e) => setInputPage(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    const toPage = Math.max(1, Number(inputPage) || 1)
                    loadList(toPage, size)
                  }
                }}
                className="h-8 w-20"
              />
              <Label className="text-sm">每页</Label>
              <Select
                value={String(inputSize)}
                onChange={(e) => {
                  const v = Number(e.target.value)
                  setInputSize(v)
                  loadList(1, Math.max(1, v))
                }}
                className="h-8 w-28"
              >
                <option value="20">20</option>
                <option value="50">50</option>
                <option value="100">100</option>
                <option value="200">200</option>
              </Select>
              <span className="text-sm text-muted-foreground">条</span>
            </div>
            <div className="ml-auto text-sm text-muted-foreground">共 {total} 条</div>
          </div>
      </CardContent>
      </Card>

      {/* 查看全文弹框 */}
      {showTextDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" role="dialog" aria-modal="true">
          <div className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-3xl border border-gray-200 dark:border-neutral-800 animate-in fade-in zoom-in-95">
            <Card className="border-0">
              <CardHeader className="pb-2">
                <CardTitle className="text-lg flex items-center gap-2">{textDialogTitle}</CardTitle>
              </CardHeader>
              <CardContent className="pt-0">
                <textarea
                  ref={textAreaRef}
                  readOnly
                  value={textDialogContent || ''}
                  className="w-full h-[50vh] text-sm leading-6 rounded-md border p-2 bg-muted/30 dark:bg-neutral-800"
                />
                <div className="flex justify-end gap-2 mt-4">
                  <Button variant="outline" onClick={selectDialogText} className="rounded-full px-4">全选</Button>
                  <Button variant="success" onClick={copyDialogText} className="rounded-full px-4">复制</Button>
                  <Button onClick={() => setShowTextDialog(false)} className="rounded-full px-4">关闭</Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}
    </div>
  )
}