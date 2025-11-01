'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { BiCog, BiEnvelope, BiBriefcase, BiSearch, BiTask, BiUserCircle } from 'react-icons/bi'

export default function Sidebar() {
  const pathname = usePathname()

  const navItems = [
    { href: '/', icon: BiCog, label: '主配置' },
    { href: '/env-config', icon: BiEnvelope, label: '环境变量' },
    { href: '/boss', icon: BiBriefcase, label: 'Boss直聘' },
    { href: '/liepin', icon: BiSearch, label: '猎聘' },
    { href: '/job51', icon: BiTask, label: '51job' },
    { href: '/zhilian', icon: BiUserCircle, label: '智联招聘' },
  ]

  return (
    <div className="sidebar">
      <div className="sidebar-header">
        <h1>💼 智能求职助手</h1>
        <p>配置管理中心</p>
      </div>
      <nav className="sidebar-nav">
        {navItems.map((item) => {
          const Icon = item.icon
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`nav-link ${pathname === item.href ? 'active' : ''}`}
            >
              <Icon size={20} />
              <span>{item.label}</span>
            </Link>
          )
        })}
      </nav>
    </div>
  )
}
