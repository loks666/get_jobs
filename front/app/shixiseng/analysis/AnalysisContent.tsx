"use client"

import { useEffect, useRef, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select } from "@/components/ui/select"
import { Label } from "@/components/ui/label"
import { BiRefresh, BiBarChart, BiLineChart, BiPieChart } from "react-icons/bi"
import { API_BASE } from "@/lib/api"

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
  }
}

type ShixisengJob = {
  jobId: string
  jobTitle?: string
  jobLink?: string
  jobSalaryText?: string
  jobArea?: string
  jobEduReq?: string
  jobExpReq?: string
  compName?: string
  compIndustry?: string
  compScale?: string
  delivered?: number
  createTime?: string
}

type PagedResult = {
  items: ShixisengJob[]
  total: number
  page: number
  size: number
}

const CATEGORY_COLORS = [
  "#0284c7",
  "#10b981",
  "#f59e0b",
  "#ef4444",
  "#6366f1",
  "#06b6d4",
  "#a78bfa",
  "#f97316",
]

function ChartCanvas({
  type,
  labels,
  data,
  color = "#0284c7",
  colors,
}: {
  type: "pie" | "bar" | "line"
  labels: string[]
  data: number[]
  color?: string
  colors?: string[]
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

    let cancelled = false

    ensureChart()
      .then((ChartCtor) => {
        if (cancelled || !canvasRef.current) return
        const palette = colors && colors.length ? colors : CATEGORY_COLORS
        chartRef.current = new ChartCtor(ctx, {
          type,
          data: {
            labels,
            datasets: [
              {
                data,
                backgroundColor: type === "line" ? "rgba(2,132,199,0.16)" : palette.slice(0, Math.max(labels.length, 1)),
                borderColor: type === "pie" ? "#ffffff" : color,
                borderWidth: type === "pie" ? 2 : 2,
                fill: type === "line",
                tension: 0.35,
              },
            ],
          },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
              legend: {
                display: type === "pie",
                position: "bottom",
              },
            },
            scales:
              type === "pie"
                ? undefined
                : {
                    y: { beginAtZero: true, ticks: { precision: 0 } },
                  },
          },
        })
      })
      .catch(() => {})

    return () => {
      cancelled = true
      if (chartRef.current) {
        chartRef.current.destroy()
        chartRef.current = null
      }
    }
  }, [type, JSON.stringify(labels), JSON.stringify(data), color])

  return (
    <div className="h-[240px] w-full">
      <canvas ref={canvasRef} />
    </div>
  )
}

export default function AnalysisContent() {
  const [statusFilter, setStatusFilter] = useState<string>("")
  const [locationFilter, setLocationFilter] = useState<string>("")
  const [degreeFilter, setDegreeFilter] = useState<string>("")
  const [keywordFilter, setKeywordFilter] = useState<string>("")
  const [stats, setStats] = useState<StatsResponse | null>(null)
  const [list, setList] = useState<PagedResult | null>(null)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)

  const fetchAll = async (targetPage = page) => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (statusFilter) params.set("statuses", statusFilter)
      if (locationFilter.trim()) params.set("location", locationFilter.trim())
      if (degreeFilter.trim()) params.set("degree", degreeFilter.trim())
      if (keywordFilter.trim()) params.set("keyword", keywordFilter.trim())

      const listParams = new URLSearchParams(params)
      listParams.set("page", String(targetPage))
      listParams.set("size", "20")

      const [statsRes, listRes] = await Promise.all([
        fetch(`${API_BASE}/api/shixiseng/stats?${params.toString()}`),
        fetch(`${API_BASE}/api/shixiseng/list?${listParams.toString()}`),
      ])
      if (statsRes.ok) setStats(await statsRes.json())
      if (listRes.ok) setList(await listRes.json())
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchAll(1)
    setPage(1)
  }, [])

  const applyFilters = () => {
    setPage(1)
    fetchAll(1)
  }

  const totalPages = list ? Math.max(1, Math.ceil(list.total / list.size)) : 1

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="flex flex-row items-center justify-between gap-4 pb-3">
          <div>
            <CardTitle className="text-base">筛选与统计刷新</CardTitle>
            <CardDescription>实时统计实习僧抓取与投递快照数据</CardDescription>
          </div>
          <Button variant="outline" size="sm" onClick={() => fetchAll(page)} disabled={loading} className="gap-1.5">
            <BiRefresh className={loading ? "animate-spin" : ""} />
            刷新数据
          </Button>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-5">
            <div>
              <Label className="text-xs">投递状态</Label>
              <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="mt-1">
                <option value="">全部</option>
                <option value="1">已投递</option>
                <option value="0">未投递</option>
              </Select>
            </div>
            <div>
              <Label className="text-xs">城市/地区</Label>
              <Input value={locationFilter} onChange={(e) => setLocationFilter(e.target.value)} placeholder="如 广州" className="mt-1" />
            </div>
            <div>
              <Label className="text-xs">学历要求</Label>
              <Input value={degreeFilter} onChange={(e) => setDegreeFilter(e.target.value)} placeholder="如 本科" className="mt-1" />
            </div>
            <div>
              <Label className="text-xs">关键词</Label>
              <Input value={keywordFilter} onChange={(e) => setKeywordFilter(e.target.value)} placeholder="岗位/公司名" className="mt-1" />
            </div>
            <div className="flex items-end">
              <Button onClick={applyFilters} className="w-full">应用筛选</Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>累计抓取岗位</CardDescription>
            <CardTitle className="text-2xl">{stats?.kpi.total ?? 0}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>已投递成功</CardDescription>
            <CardTitle className="text-2xl text-emerald-600">{stats?.kpi.delivered ?? 0}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>未投递/已跳过</CardDescription>
            <CardTitle className="text-2xl text-amber-600">{stats?.kpi.pending ?? 0}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>投递转化率</CardDescription>
            <CardTitle className="text-2xl text-sky-600">
              {stats && stats.kpi.total > 0 ? `${Math.round((stats.kpi.delivered / stats.kpi.total) * 100)}%` : "0%"}
            </CardTitle>
          </CardHeader>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-sm">
              <BiPieChart className="text-sky-500" /> 投递状态分布
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ChartCanvas
              type="pie"
              labels={(stats?.charts.byStatus || []).map((i) => i.name)}
              data={(stats?.charts.byStatus || []).map((i) => i.value)}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-sm">
              <BiBarChart className="text-sky-500" /> 城市分布 Top 10
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ChartCanvas
              type="bar"
              labels={(stats?.charts.byCity || []).map((i) => i.name)}
              data={(stats?.charts.byCity || []).map((i) => i.value)}
              color="#0284c7"
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-sm">
              <BiLineChart className="text-emerald-500" /> 每日抓取趋势
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ChartCanvas
              type="line"
              labels={(stats?.charts.dailyTrend || []).map((i) => i.name)}
              data={(stats?.charts.dailyTrend || []).map((i) => i.value)}
              color="#10b981"
            />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">岗位明细（共 {list?.total ?? 0} 条）</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b text-xs text-muted-foreground">
                  <th className="py-2 pr-3">岗位名称</th>
                  <th className="py-2 pr-3">公司</th>
                  <th className="py-2 pr-3">薪资</th>
                  <th className="py-2 pr-3">城市</th>
                  <th className="py-2 pr-3">学历</th>
                  <th className="py-2 pr-3">实习周期</th>
                  <th className="py-2 pr-3">状态</th>
                  <th className="py-2">记录时间</th>
                </tr>
              </thead>
              <tbody>
                {(list?.items || []).map((job) => (
                  <tr key={job.jobId} className="border-b last:border-0 hover:bg-muted/40">
                    <td className="py-2.5 pr-3 font-medium">
                      {job.jobLink ? (
                        <a href={job.jobLink} target="_blank" rel="noreferrer" className="text-sky-600 hover:underline">
                          {job.jobTitle || job.jobId}
                        </a>
                      ) : (
                        job.jobTitle || job.jobId
                      )}
                    </td>
                    <td className="py-2.5 pr-3">{job.compName || "-"}</td>
                    <td className="py-2.5 pr-3">{job.jobSalaryText || "-"}</td>
                    <td className="py-2.5 pr-3">{job.jobArea || "-"}</td>
                    <td className="py-2.5 pr-3">{job.jobEduReq || "-"}</td>
                    <td className="py-2.5 pr-3">{job.jobExpReq || "-"}</td>
                    <td className="py-2.5 pr-3">
                      <span
                        className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                          job.delivered === 1
                            ? "bg-emerald-500/15 text-emerald-600"
                            : "bg-slate-500/15 text-slate-500"
                        }`}
                      >
                        {job.delivered === 1 ? "已投递" : "未投递"}
                      </span>
                    </td>
                    <td className="py-2.5 text-xs text-muted-foreground">{job.createTime?.replace("T", " ").slice(0, 16) || "-"}</td>
                  </tr>
                ))}
                {(!list || list.items.length === 0) && (
                  <tr>
                    <td colSpan={8} className="py-8 text-center text-muted-foreground">
                      暂无实习僧岗位记录
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex items-center justify-between text-xs text-muted-foreground">
            <span>
              第 {page} / {totalPages} 页
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page <= 1 || loading}
                onClick={() => {
                  const next = Math.max(1, page - 1)
                  setPage(next)
                  fetchAll(next)
                }}
              >
                上一页
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page >= totalPages || loading}
                onClick={() => {
                  const next = Math.min(totalPages, page + 1)
                  setPage(next)
                  fetchAll(next)
                }}
              >
                下一页
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
