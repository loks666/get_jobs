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
  deliveryStatus?: string
  jobUrl?: string
  createdAt?: string
}

type PagedResult = {
  items: BossJob[]
  total: number
  page: number
  size: number
}

const API_BASE = "http://localhost:8888"

function ChartCanvas({
  type,
  labels,
  data,
  title,
  color = "#3b82f6",
}: {
  type: "pie" | "bar" | "line"
  labels: string[]
  data: number[]
  title?: string
  color?: string
}) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const chartRef = useRef<any | null>(null)

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

    const dataset = {
      label: title || "",
      data,
      backgroundColor:
        type === "pie"
          ? [
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
          : color,
      borderColor: color,
    }

    ;(async () => {
      const Chart = await ensureChart()
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
    })()

    return () => {
      chartRef.current?.destroy()
    }
  }, [type, labels, data, title, color])

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

  const [statuses, setStatuses] = useState<string[]>(["未投递", "已投递", "已过滤", "投递失败"]) // 默认全选
  const [location, setLocation] = useState<string>("")
  const [experience, setExperience] = useState<string>("")
  const [degree, setDegree] = useState<string>("")
  const [minK, setMinK] = useState<string>("")
  const [maxK, setMaxK] = useState<string>("")
  const [keyword, setKeyword] = useState<string>("")
  const [loadingList, setLoadingList] = useState(false)
  const [reloading, setReloading] = useState(false)
  const [exporting, setExporting] = useState(false)

  const statusOptions = ["未投递", "已投递", "已过滤", "投递失败"]

  useEffect(() => {
    ;(async () => {
      try {
        setLoadingStats(true)
        const res = await fetch(`${API_BASE}/api/boss/stats`)
        const data: StatsResponse = await res.json()
        setStats(data)
      } catch (e) {
        console.error("fetch stats failed", e)
      } finally {
        setLoadingStats(false)
      }
    })()
  }, [])

  // 当实际页码/每页条数变化时，同步到输入框
  useEffect(() => {
    setInputPage(page)
  }, [page])
  useEffect(() => {
    setInputSize(size)
  }, [size])

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
    params.set("page", String(toPage))
    params.set("size", String(toSize))

    try {
      setLoadingList(true)
      const res = await fetch(`${API_BASE}/api/boss/list?${params.toString()}`)
      const data: PagedResult = await res.json()
      setItems(data.items || [])
      setTotal(data.total || 0)
      setPage(data.page || toPage)
      setSize(data.size || toSize)
    } catch (e) {
      console.error("fetch list failed", e)
    } finally {
      setLoadingList(false)
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
      const resStats = await fetch(`${API_BASE}/api/boss/stats`)
      const statsData: StatsResponse = await resStats.json()
      setStats(statsData)
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
            <div className="flex flex-wrap gap-3">
              {statusOptions.map((s) => (
                <label key={s} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={statuses.includes(s)}
                    onChange={(e) => {
                      setStatuses((prev) => {
                        if (e.target.checked) return Array.from(new Set([...prev, s]))
                        return prev.filter((x) => x !== s)
                      })
                    }}
                  />
                  {s}
                </label>
              ))}
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
            <Button onClick={() => loadList(1, size)} disabled={loadingList}>
              <BiBarChart className="mr-2" /> 应用筛选
            </Button>
            <Button variant="secondary" onClick={exportCSV} disabled={exporting}>
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
            <CardTitle className="text-base flex items-center gap-2"><BiBarChart /> 城市TOP10</CardTitle>
            <CardDescription>岗位按城市聚合</CardDescription>
          </CardHeader>
          <CardContent>
            {stats ? (
              <ChartCanvas
                type="bar"
                labels={stats.charts.byCity.map((x) => x.name)}
                data={stats.charts.byCity.map((x) => x.value)}
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
              <ChartCanvas type="bar" labels={stats.charts.byCompany.map((x) => x.name)} data={stats.charts.byCompany.map((x) => x.value)} />
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
              <ChartCanvas type="bar" labels={stats.charts.byExperience.map((x) => x.name)} data={stats.charts.byExperience.map((x) => x.value)} />
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
              <ChartCanvas type="bar" labels={stats.charts.byDegree.map((x) => x.name)} data={stats.charts.byDegree.map((x) => x.value)} />
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
              <ChartCanvas type="line" labels={stats.charts.salaryBuckets.map((x) => x.bucket)} data={stats.charts.salaryBuckets.map((x) => x.value)} />
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
          <div className="rounded-lg border overflow-x-auto">
            <table className="w-full table-fixed">
              <thead className="bg-muted/50">
                <tr className="border-b">
                  <th className="w-48 px-4 py-3 text-left text-sm leading-6 font-medium whitespace-nowrap">公司名称</th>
                  <th className="w-64 px-4 py-3 text-left text-sm leading-6 font-medium whitespace-nowrap">岗位名称</th>
                  <th className="w-24 px-4 py-3 text-left text-sm leading-6 font-medium whitespace-nowrap">薪资</th>
                  <th className="w-20 px-4 py-3 text-left text-sm leading-6 font-medium whitespace-nowrap">地点</th>
                  <th className="w-24 px-4 py-3 text-left text-sm leading-6 font-medium whitespace-nowrap">经验</th>
                  <th className="w-20 px-4 py-3 text-left text-sm leading-6 font-medium whitespace-nowrap">学历</th>
                  <th className="w-24 px-4 py-3 text-left text-sm leading-6 font-medium whitespace-nowrap">HR</th>
                  <th className="w-20 px-4 py-3 text-left text-sm leading-6 font-medium whitespace-nowrap">状态</th>
                  <th className="w-16 px-4 py-3 text-left text-sm leading-6 font-medium whitespace-nowrap">链接</th>
                  <th className="w-28 px-4 py-3 text-left text-sm leading-6 font-medium whitespace-nowrap">创建时间</th>
                </tr>
              </thead>
              <tbody>
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={10} className="px-4 py-8 text-center text-muted-foreground">暂无数据</td>
                  </tr>
                ) : (
                  items.map((it) => (
                    <tr key={it.id} className="border-b">
                      <td className="px-4 py-2 text-sm leading-6 whitespace-nowrap align-middle overflow-hidden">
                        <span className="block truncate" title={it.companyName}>{it.companyName}</span>
                      </td>
                      <td className="px-4 py-2 text-sm leading-6 whitespace-nowrap align-middle overflow-hidden">
                        <span className="block truncate" title={it.jobName}>{it.jobName}</span>
                      </td>
                      <td className="px-4 py-2 text-sm leading-6 whitespace-nowrap align-middle">{it.salary}</td>
                      <td className="px-4 py-2 text-sm leading-6 whitespace-nowrap align-middle">{it.location}</td>
                      <td className="px-4 py-2 text-sm leading-6 whitespace-nowrap align-middle">{it.experience}</td>
                      <td className="px-4 py-2 text-sm leading-6 whitespace-nowrap align-middle">{it.degree}</td>
                      <td className="px-4 py-2 text-sm leading-6 whitespace-nowrap align-middle overflow-hidden">
                        <span className="block truncate" title={it.hrName}>{it.hrName}</span>
                      </td>
                      <td className="px-4 py-2 text-sm leading-6 whitespace-nowrap align-middle">{it.deliveryStatus}</td>
                      <td className="px-4 py-2 text-sm leading-6 whitespace-nowrap align-middle">
                        {it.jobUrl ? (
                          <a href={it.jobUrl} className="text-primary underline" target="_blank" rel="noreferrer">链接</a>
                        ) : (
                          "-"
                        )}
                      </td>
                      <td className="px-4 py-2 text-sm leading-6 whitespace-nowrap align-middle">{formatDateOnly(it.createdAt)}</td>
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
              <Button
                variant="secondary"
                className="h-8"
                onClick={() => {
                  const toPage = Math.max(1, Number(inputPage) || 1)
                  const toSize = Math.max(1, Number(inputSize) || size)
                  loadList(toPage, toSize)
                }}
                disabled={loadingList}
              >应用分页</Button>
            </div>
            <div className="ml-auto text-sm text-muted-foreground">共 {total} 条</div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}