'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { BiCog, BiEnvelope, BiBriefcase, BiSearch, BiTask, BiUserCircle, BiStar, BiTrendingUp } from 'react-icons/bi'

export default function Sidebar() {
  const pathname = usePathname()

  const navItems = [
    { href: '/', icon: BiCog, label: '主配置', color: 'text-blue-300' },
    { href: '/env-config', icon: BiEnvelope, label: '环境变量', color: 'text-cyan-300' },
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
          <div className="p-2 bg-white/20 rounded-xl backdrop-blur-sm">
            <BiTrendingUp className="text-2xl text-white" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-white">智能求职助手</h1>
            <p className="text-white/80 text-sm">配置管理中心</p>
          </div>
        </div>

        {/* 状态指示器 */}
        <div className="mt-4 flex items-center gap-2 text-white/90 text-sm">
          <div className="w-2 h-2 bg-green-400 rounded-full animate-pulse"></div>
          <span>系统运行正常</span>
        </div>
      </div>

      {/* 导航菜单 */}
      <nav className="p-4 space-y-2">
        {navItems.map((item, index) => {
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

              {/* 活跃状态指示器 */}
              {isActive && (
                <div className="ml-auto">
                  <div className="w-2 h-2 bg-cyan-300 rounded-full animate-pulse"></div>
                </div>
              )}
            </Link>
          )
        })}
      </nav>

      {/* 底部信息 */}
      <div className="absolute bottom-0 left-0 right-0 p-4">
        <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4 border border-white/20">
          <div className="flex items-center gap-3 mb-3">
            <BiStar className="text-yellow-300 text-lg" />
            <span className="text-white font-medium text-sm">快速统计</span>
          </div>

          <div className="grid grid-cols-2 gap-3 text-center">
            <div className="bg-white/10 rounded-lg p-2">
              <div className="text-lg font-bold text-white">4</div>
              <div className="text-xs text-white/70">平台</div>
            </div>
            <div className="bg-white/10 rounded-lg p-2">
              <div className="text-lg font-bold text-cyan-300">6</div>
              <div className="text-xs text-white/70">配置</div>
            </div>
          </div>
        </div>

        {/* 版本信息 */}
        <div className="mt-3 text-center">
          <p className="text-white/60 text-xs">v1.0.0</p>
        </div>
      </div>
    </div>
  )
}
