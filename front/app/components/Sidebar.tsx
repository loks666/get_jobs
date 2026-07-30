'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { BiEnvelope, BiBriefcase, BiSearch, BiTask, BiUserCircle, BiBrain, BiMoon, BiSun } from 'react-icons/bi'
import { motion } from 'framer-motion'
import { useTheme } from 'next-themes'

export default function Sidebar() {
  const pathname = usePathname()
  const { theme, setTheme } = useTheme()
  const [mounted, setMounted] = useState(false)

  // 健康检查状态：up / degraded / down / unknown
  const [health, setHealth] = useState<'up' | 'degraded' | 'down' | 'unknown'>('unknown')
  const checkingRef = useRef(false)

  useEffect(() => {
    setMounted(true)
  }, [])

  useEffect(() => {
    let interval: NodeJS.Timeout | null = null

    const check = async () => {
      if (checkingRef.current) return
      checkingRef.current = true
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
      } catch {
        setHealth('unknown')
      } finally {
        clearTimeout(timeout)
        checkingRef.current = false
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
    { href: '/51job', icon: BiTask, label: '51job', color: 'text-blue-300' },
    { href: '/zhilian', icon: BiUserCircle, label: '智联招聘', color: 'text-cyan-300' },
  ]

  return (
    <motion.div
      initial={{ x: -100, opacity: 0 }}
      animate={{ x: 0, opacity: 1 }}
      transition={{ duration: 0.5, ease: "easeOut" }}
      className="fixed left-0 top-0 h-full w-64 bg-gradient-to-b from-blue-600 via-indigo-600 to-purple-600 dark:from-blacksection dark:via-blackho dark:to-black shadow-solid-8 z-50 border-r border-white/10 dark:border-strokedark"
    >
      {/* 侧边栏头部 */}
      <motion.div
        initial={{ y: -20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ delay: 0.2, duration: 0.5 }}
        className="p-6 border-b border-white/20 dark:border-strokedark"
      >
        <div className="flex items-center gap-3 mb-2">
          <span className="text-4xl leading-none">🍀</span>
          <div>
            <h1 className="text-xl font-bold text-white">Get Jobs</h1>
            <p className="text-white dark:text-manatee text-sm">配置管理中心</p>
          </div>
        </div>

        {/* 状态指示器（动态健康检查） */}
        <div className="mt-4 flex items-center gap-2 text-white dark:text-manatee text-sm">
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
          <span className="text-white dark:text-manatee">
            {health === 'up'
              ? '系统运行正常'
              : health === 'degraded'
              ? '服务降级'
              : health === 'down'
              ? '服务异常'
              : '未连接'}
          </span>
        </div>

        {/* 主题切换器 */}
        {mounted && (
          <motion.button
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ delay: 0.4, type: "spring", stiffness: 200 }}
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            className="mt-4 w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-white/10 hover:bg-white/20 dark:bg-white/5 dark:hover:bg-white/10 text-white transition-all duration-300 shadow-solid-3"
          >
            {theme === 'dark' ? (
              <>
                <BiSun className="text-lg" />
                <span className="text-sm">切换到浅色</span>
              </>
            ) : (
              <>
                <BiMoon className="text-lg" />
                <span className="text-sm">切换到深色</span>
              </>
            )}
          </motion.button>
        )}
      </motion.div>

      {/* 导航菜单 */}
      <nav className="p-4 space-y-4 overflow-y-auto h-[calc(100vh-280px)]">
        {/* 环境配置分组 */}
        <div>
          <div className="px-4 py-2 text-white dark:text-waterloo text-xs uppercase tracking-wide">环境配置</div>
          <div className="space-y-2">
            {envGroup.map((item, index) => {
              const Icon = item.icon
              const isActive = pathname === item.href
              return (
                <motion.div
                  key={item.href}
                  initial={{ x: -20, opacity: 0 }}
                  animate={{ x: 0, opacity: 1 }}
                  transition={{ delay: 0.1 * index + 0.3, duration: 0.3 }}
                >
                  <Link
                    href={item.href}
                    className={`
                      group flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-300
                      ${isActive
                        ? 'bg-white/25 dark:bg-white/5 text-white shadow-solid-3 backdrop-blur-sm border-l-4 border-cyan-300'
                        : 'text-white dark:text-manatee hover:bg-white/15 dark:hover:bg-white/5 hover:translate-x-1'
                      }
                    `}
                  >
                    <Icon className={`text-xl ${isActive ? 'text-cyan-300' : item.color} group-hover:scale-110 transition-transform`} />
                    <span className="font-medium">{item.label}</span>
                    {isActive && (
                      <div className="ml-auto">
                        <div className="w-2 h-2 bg-cyan-300 rounded-full animate-pulse"></div>
                      </div>
                    )}
                  </Link>
                </motion.div>
              )
            })}
          </div>
        </div>

        {/* 平台配置分组 */}
        <div>
          <div className="px-4 py-2 text-white dark:text-waterloo text-xs uppercase tracking-wide">平台配置</div>
          <div className="space-y-2">
            {platformGroup.map((item, index) => {
              const Icon = item.icon
              const isActive = pathname === item.href
              return (
                <motion.div
                  key={item.href}
                  initial={{ x: -20, opacity: 0 }}
                  animate={{ x: 0, opacity: 1 }}
                  transition={{ delay: 0.1 * index + 0.5, duration: 0.3 }}
                >
                  <Link
                    href={item.href}
                    className={`
                      group flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-300
                      ${isActive
                        ? 'bg-white/25 dark:bg-white/5 text-white shadow-solid-3 backdrop-blur-sm border-l-4 border-cyan-300'
                        : 'text-white dark:text-manatee hover:bg-white/15 dark:hover:bg-white/5 hover:translate-x-1'
                      }
                    `}
                  >
                    <Icon className={`text-xl ${isActive ? 'text-cyan-300' : item.color} group-hover:scale-110 transition-transform`} />
                    <span className="font-medium">{item.label}</span>
                    {isActive && (
                      <div className="ml-auto">
                        <div className="w-2 h-2 bg-cyan-300 rounded-full animate-pulse"></div>
                      </div>
                    )}
                  </Link>
                </motion.div>
              )
            })}
          </div>
        </div>
      </nav>

      {/* 底部信息 */}
      <motion.div
        initial={{ y: 20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ delay: 0.8, duration: 0.5 }}
        className="absolute bottom-0 left-0 right-0 p-4 border-t border-white/10 dark:border-strokedark"
      >
        {/* 版本信息 */}
        <div className="text-center">
          <p className="text-white/60 dark:text-waterloo text-xs">v1.0.0</p>
        </div>
      </motion.div>
    </motion.div>
  )
}
