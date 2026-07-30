"use client"

import { useEffect, useRef } from "react"

type ChartType = "pie" | "bar" | "line"

type ChartDataset = {
  label: string
  data: number[]
  backgroundColor: string | string[]
  borderColor?: string | string[]
  fill?: boolean
  pointBackgroundColor?: string
  pointBorderColor?: string
}

type ChartConfiguration = {
  type: ChartType
  data: {
    labels: string[]
    datasets: ChartDataset[]
  }
  options: {
    responsive: boolean
    maintainAspectRatio: boolean
    plugins: {
      legend: { display: boolean }
      title: { display: boolean; text?: string }
    }
    scales?: {
      x: { ticks: { autoSkip: boolean } }
      y: { beginAtZero: boolean }
    }
  }
}

type ChartInstance = {
  destroy: () => void
}

type ChartConstructor = new (
  context: CanvasRenderingContext2D,
  configuration: ChartConfiguration,
) => ChartInstance

declare global {
  interface Window {
    Chart?: ChartConstructor
  }
}

export type ChartCanvasProps = {
  type: ChartType
  labels: string[]
  data: number[]
  title?: string
  color?: string
  colors?: string[]
}

const DEFAULT_COLOR = "#3b82f6"
const PIE_COLORS = [
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

const getChartConstructor = () => {
  if (!window.Chart) {
    throw new Error("Chart.js loaded without exposing window.Chart")
  }
  return window.Chart
}

const ensureChart = async (): Promise<ChartConstructor> => {
  if (window.Chart) {
    return window.Chart
  }

  return new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(
      "script[data-chartjs-cdn='true']",
    )
    if (existing) {
      existing.addEventListener("load", () => resolve(getChartConstructor()), {
        once: true,
      })
      existing.addEventListener(
        "error",
        () => reject(new Error("Chart.js CDN load error")),
        { once: true },
      )
      return
    }

    const script = document.createElement("script")
    script.src = "https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js"
    script.async = true
    script.dataset.chartjsCdn = "true"
    script.addEventListener("load", () => resolve(getChartConstructor()), {
      once: true,
    })
    script.addEventListener(
      "error",
      () => reject(new Error("Chart.js CDN load error")),
      { once: true },
    )
    document.head.appendChild(script)
  })
}

export default function ChartCanvas({
  type,
  labels,
  data,
  title,
  color = DEFAULT_COLOR,
  colors,
}: ChartCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const chartRef = useRef<ChartInstance | null>(null)

  useEffect(() => {
    const context = canvasRef.current?.getContext("2d")
    if (!context) {
      return
    }

    chartRef.current?.destroy()
    chartRef.current = null
    let cancelled = false

    const backgroundColor =
      type === "pie"
        ? (colors?.length ? colors : PIE_COLORS).slice(0, labels.length)
        : type === "bar" && colors?.length
          ? colors.slice(0, data.length)
          : color
    const borderColor =
      type === "pie"
        ? undefined
        : type === "bar" && colors?.length
          ? colors.slice(0, data.length)
          : color
    const dataset: ChartDataset = {
      label: title ?? "",
      data,
      backgroundColor,
      borderColor,
      ...(type === "line"
        ? {
            fill: false,
            pointBackgroundColor: color,
            pointBorderColor: color,
          }
        : {}),
    }

    void ensureChart()
      .then((Chart) => {
        if (cancelled) {
          return
        }
        chartRef.current = new Chart(context, {
          type,
          data: { labels, datasets: [dataset] },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
              legend: { display: type === "pie" },
              title: { display: Boolean(title), text: title },
            },
            scales:
              type === "pie"
                ? undefined
                : {
                    x: { ticks: { autoSkip: true } },
                    y: { beginAtZero: true },
                  },
          },
        })
      })
      .catch((error: unknown) => {
        console.error("Failed to create chart:", error)
      })

    return () => {
      cancelled = true
      chartRef.current?.destroy()
      chartRef.current = null
    }
  }, [type, labels, data, title, color, colors])

  return <canvas ref={canvasRef} className="w-full h-64" />
}
