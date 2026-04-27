'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useState } from 'react'
import { BiEnvelope, BiBriefcase, BiSearch, BiTask, BiUserCircle, BiBrain, BiMoon, BiSun, BiHeart } from 'react-icons/bi'
import { motion, type Transition } from 'framer-motion'
import { useTheme } from 'next-themes'

export default function Sidebar() {
  const pathname = usePathname()
  const { theme, setTheme } = useTheme()
  const [mounted, setMounted] = useState(false)

  const [health, setHealth] = useState<'up' | 'degraded' | 'down' | 'unknown'>('unknown')
  const [checking, setChecking] = useState(false)

  useEffect(() => { setMounted(true) }, [])

  useEffect(() => {
    let interval: NodeJS.Timeout | null = null

    const check = async () => {
      if (checking) return
      setChecking(true)
      const baseUrl = process.env.API_BASE_URL || 'http://localhost:8888'
      const controller = new AbortController()
      const timeout = setTimeout(() => controller.abort(), 3000)
      try {
        let res = await fetch(`${baseUrl}/api/health`, { signal: controller.signal })
        if (res.status === 404) {
          res = await fetch(`${baseUrl}/actuator/health`, { signal: controller.signal })
        }
        if (!res.ok) throw new Error(`status ${res.status}`)
        const data = await res.json()
        const statusRaw = (data.status || data.state || '').toString().toUpperCase()
        if (statusRaw === 'UP' || statusRaw === 'HEALTHY') setHealth('up')
        else if (statusRaw === 'DEGRADED' || statusRaw === 'WARN') setHealth('degraded')
        else setHealth('down')
      } catch { setHealth('unknown') }
      finally { clearTimeout(timeout); setChecking(false) }
    }

    check()
    interval = setInterval(check, 30000)
    return () => { if (interval) clearInterval(interval) }
  }, [])

  const envGroup = [
    { href: '/env-config', icon: BiEnvelope, label: '环境配置', color: 'text-amber-400' },
    { href: '/ai-config', icon: BiBrain, label: 'AI配置', color: 'text-violet-400' },
  ]

  const platformGroup = [
    { href: '/boss', icon: BiBriefcase, label: 'Boss直聘', color: 'text-emerald-400' },
    { href: '/liepin', icon: BiSearch, label: '猎聘', color: 'text-orange-400' },
    { href: '/51job', icon: BiTask, label: '51job', color: 'text-rose-400' },
    { href: '/zhilian', icon: BiUserCircle, label: '智联招聘', color: 'text-sky-400' },
  ]

  const navItemVariants = {
    hidden: { opacity: 0, x: -12 },
    visible: (i: number) => ({
      opacity: 1, x: 0,
      transition: { delay: i * 0.05, duration: 0.3, ease: "easeOut" as const }
    }),
  }

  return (
    <motion.aside
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.4, ease: "easeOut" }}
      className="fixed left-0 top-0 h-full w-64 z-50 flex flex-col
        bg-gradient-to-b from-slate-900 via-slate-900 to-slate-950
        border-r border-white/[0.06]"
    >
      {/* ── Logo 区域 ── */}
      <motion.div
        initial={{ y: -16, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ delay: 0.1, duration: 0.4 }}
        className="px-5 pt-6 pb-5 border-b border-white/[0.06]"
      >
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-amber-400 to-amber-600 flex items-center justify-center shadow-glow-amber">
            <span className="text-xl leading-none">🍀</span>
          </div>
          <div>
            <h1 className="text-lg font-bold font-display text-white tracking-tight">Get Jobs</h1>
            <p className="text-[11px] text-slate-400 tracking-wide">配置管理中心</p>
          </div>
        </div>

        {/* 健康状态 */}
        <div className="mt-4 flex items-center gap-2 px-3 py-2 rounded-lg bg-white/[0.04]">
          <div className={`w-1.5 h-1.5 rounded-full ${
            health === 'up' ? 'bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.6)]' :
            health === 'degraded' ? 'bg-amber-400 shadow-[0_0_6px_rgba(251,191,36,0.6)]' :
            health === 'down' ? 'bg-rose-400 shadow-[0_0_6px_rgba(251,113,133,0.6)]' :
            'bg-slate-500'
          } ${health !== 'unknown' ? 'animate-pulse' : ''}`} />
          <span className="text-xs text-slate-400">
            {health === 'up' ? '系统运行正常' :
             health === 'degraded' ? '服务降级' :
             health === 'down' ? '服务异常' : '未连接'}
          </span>
        </div>
      </motion.div>

      {/* ── 导航 ── */}
      <nav className="flex-1 px-3 py-4 space-y-5 overflow-y-auto no-scrollbar">
        {/* 环境配置 */}
        <div>
          <div className="px-3 mb-1.5 text-[10px] uppercase tracking-[0.12em] text-slate-500 font-medium">环境配置</div>
          <div className="space-y-0.5">
            {envGroup.map((item, i) => {
              const Icon = item.icon
              const isActive = pathname === item.href
              return (
                <motion.div key={item.href} variants={navItemVariants} initial="hidden" animate="visible" custom={i}>
                  <Link
                    href={item.href}
                    className={`group flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-all duration-200
                      ${isActive
                        ? 'bg-amber-500/[0.12] text-amber-300 shadow-glow-amber'
                        : 'text-slate-400 hover:text-slate-200 hover:bg-white/[0.05]'
                      }`}
                  >
                    <Icon className={`text-lg transition-colors ${isActive ? 'text-amber-400' : item.color + ' group-hover:' + item.color}`} />
                    <span className={`font-medium ${isActive ? 'text-amber-200' : ''}`}>{item.label}</span>
                    {isActive && (
                      <div className="ml-auto w-1 h-4 rounded-full bg-amber-400/80" />
                    )}
                  </Link>
                </motion.div>
              )
            })}
          </div>
        </div>

        {/* 平台配置 */}
        <div>
          <div className="px-3 mb-1.5 text-[10px] uppercase tracking-[0.12em] text-slate-500 font-medium">平台配置</div>
          <div className="space-y-0.5">
            {platformGroup.map((item, i) => {
              const Icon = item.icon
              const isActive = pathname === item.href || pathname.startsWith(item.href + '/')
              return (
                <motion.div key={item.href} variants={navItemVariants} initial="hidden" animate="visible" custom={i + 2}>
                  <Link
                    href={item.href}
                    className={`group flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-all duration-200
                      ${isActive
                        ? 'bg-white/[0.08] text-white shadow-glow-blue'
                        : 'text-slate-400 hover:text-slate-200 hover:bg-white/[0.05]'
                      }`}
                  >
                    <Icon className={`text-lg transition-colors ${isActive ? item.color : item.color + '/60 group-hover:' + item.color.replace('400', '300')}`} />
                    <span className={`font-medium ${isActive ? 'text-white' : ''}`}>{item.label}</span>
                    {isActive && (
                      <div className={`ml-auto w-1 h-4 rounded-full ${
                        item.href === '/boss' ? 'bg-emerald-400/80' :
                        item.href === '/liepin' ? 'bg-orange-400/80' :
                        item.href === '/51job' ? 'bg-rose-400/80' :
                        'bg-sky-400/80'
                      }`} />
                    )}
                  </Link>
                </motion.div>
              )
            })}
          </div>
        </div>
      </nav>

      {/* ── 底部 ── */}
      <motion.div
        initial={{ y: 16, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ delay: 0.5, duration: 0.4 }}
        className="px-3 pb-4 space-y-2 border-t border-white/[0.06]"
      >
        {/* 主题切换 */}
        {mounted && (
          <button
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            className="w-full mt-3 flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm
              text-slate-400 hover:text-slate-200 hover:bg-white/[0.05] transition-all duration-200"
          >
            {theme === 'dark' ? (
              <>
                <BiSun className="text-lg text-amber-400/70" />
                <span>切换到浅色</span>
              </>
            ) : (
              <>
                <BiMoon className="text-lg text-indigo-400/70" />
                <span>切换到深色</span>
              </>
            )}
          </button>
        )}

        <div className="px-3 py-2 flex items-center gap-2 text-slate-500">
          <BiHeart className="text-xs text-rose-500/40" />
          <span className="text-[10px]">v1.0.0</span>
        </div>
      </motion.div>
    </motion.aside>
  )
}
