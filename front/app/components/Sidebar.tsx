'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useState } from 'react'
import { BiEnvelope, BiBriefcase, BiSearch, BiTask, BiUserCircle, BiBrain } from 'react-icons/bi'

export default function Sidebar() {
  const pathname = usePathname()

  // 健康检查状态：up / degraded / down / unknown
  const [health, setHealth] = useState<'up' | 'degraded' | 'down' | 'unknown'>('unknown')
  const [checking, setChecking] = useState(false)

  useEffect(() => {
    let interval: NodeJS.Timeout | null = null

    const check = async () => {
      if (checking) return
      setChecking(true)
      const baseUrl = process.env.API_BASE_URL || 'http://localhost:8888'

      const controller = new AbortController()
      const timeout = setTimeout(() => controller.abort(), 3000)
      try {
        // 先尝试自定义健康接口
        let res = await fetch(`${baseUrl}/api/health`, { signal: controller.signal })
        if (res.status === 404) {
          // 回退到 Spring Boot Actuator
          res = await fetch(`${baseUrl}/actuator/health`, { signal: controller.signal })
        }
        if (!res.ok) throw new Error(`status ${res.status}`)
        const data = await res.json()
        const statusRaw = (data.status || data.state || '').toString().toUpperCase()
        if (statusRaw === 'UP' || statusRaw === 'HEALTHY') {
          setHealth('up')
        } else if (statusRaw === 'DEGRADED' || statusRaw === 'WARN') {
          setHealth('degraded')
        } else {
          setHealth('down')
        }
      } catch (e) {
        setHealth('unknown')
      } finally {
        clearTimeout(timeout)
        setChecking(false)
      }
    }

    // 首次检查 + 轮询
    check()
    interval = setInterval(check, 30000)
    return () => {
      if (interval) clearInterval(interval)
    }
  }, [])

  const envGroup = [
    { href: '/env-config', icon: BiEnvelope, label: '环境配置', color: 'text-cyan-300' },
    { href: '/ai-config', icon: BiBrain, label: 'AI配置', color: 'text-purple-300' },
  ]

  const platformGroup = [
    { href: '/boss', icon: BiBriefcase, label: 'Boss直聘', color: 'text-indigo-300' },
    { href: '/liepin', icon: BiSearch, label: '猎聘', color: 'text-purple-300' },
    { href: '/job51', icon: BiTask, label: '51job', color: 'text-blue-300' },
    { href: '/zhilian', icon: BiUserCircle, label: '智联招聘', color: 'text-cyan-300' },
  ]

  return (
    <div className="fixed left-0 top-0 h-full w-64 bg-gradient-to-b from-blue-600 via-indigo-600 to-purple-600 shadow-2xl z-50">
      {/* 侧边栏头部 */}
      <div className="p-6 border-b border-white/20">
        <div className="flex items-center gap-3 mb-2">
          <span className="text-4xl leading-none">🍀</span>
          <div>
            <h1 className="text-xl font-bold text-white">Get Jobs</h1>
            <p className="text-white/80 text-sm">配置管理中心</p>
          </div>
        </div>

        {/* 状态指示器（动态健康检查） */}
        <div className="mt-4 flex items-center gap-2 text-white/90 text-sm">
          <div
            className={`w-2 h-2 rounded-full animate-pulse ${
              health === 'up'
                ? 'bg-green-400'
                : health === 'degraded'
                ? 'bg-yellow-400'
                : health === 'down'
                ? 'bg-red-500'
                : 'bg-gray-400'
            }`}
          ></div>
          <span>
            {health === 'up'
              ? '系统运行正常'
              : health === 'degraded'
              ? '服务降级'
              : health === 'down'
              ? '服务异常'
              : '未连接'}
          </span>
        </div>
      </div>

      {/* 导航菜单 */}
      <nav className="p-4 space-y-4">
        {/* 环境配置分组 */}
        <div>
          <div className="px-4 py-2 text-white/70 text-xs uppercase tracking-wide">环境配置</div>
          <div className="space-y-2">
            {envGroup.map((item, index) => {
              const Icon = item.icon
              const isActive = pathname === item.href
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`
                    group flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-300
                    ${isActive
                      ? 'bg-white/25 text-white shadow-lg backdrop-blur-sm border-l-4 border-cyan-300'
                      : 'text-white/80 hover:bg-white/15 hover:text-white hover:translate-x-1'
                    }
                  `}
                  style={{ animationDelay: `${index * 0.1}s` }}
                >
                  <Icon className={`text-xl ${isActive ? 'text-cyan-300' : item.color} group-hover:scale-110 transition-transform`} />
                  <span className="font-medium">{item.label}</span>
                  {isActive && (
                    <div className="ml-auto">
                      <div className="w-2 h-2 bg-cyan-300 rounded-full animate-pulse"></div>
                    </div>
                  )}
                </Link>
              )
            })}
          </div>
        </div>

        {/* 平台配置分组 */}
        <div>
          <div className="px-4 py-2 text-white/70 text-xs uppercase tracking-wide">平台配置</div>
          <div className="space-y-2">
            {platformGroup.map((item, index) => {
              const Icon = item.icon
              const isActive = pathname === item.href
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`
                    group flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-300
                    ${isActive
                      ? 'bg-white/25 text-white shadow-lg backdrop-blur-sm border-l-4 border-cyan-300'
                      : 'text-white/80 hover:bg-white/15 hover:text-white hover:translate-x-1'
                    }
                  `}
                  style={{ animationDelay: `${index * 0.1}s` }}
                >
                  <Icon className={`text-xl ${isActive ? 'text-cyan-300' : item.color} group-hover:scale-110 transition-transform`} />
                  <span className="font-medium">{item.label}</span>
                  {isActive && (
                    <div className="ml-auto">
                      <div className="w-2 h-2 bg-cyan-300 rounded-full animate-pulse"></div>
                    </div>
                  )}
                </Link>
              )
            })}
          </div>
        </div>
      </nav>

      {/* 底部信息 */}
      <div className="absolute bottom-0 left-0 right-0 p-4">
        {/* 版本信息 */}
        <div className="mt-3 text-center">
          <p className="text-white/60 text-xs">v1.0.0</p>
        </div>
      </div>
    </div>
  )
}
