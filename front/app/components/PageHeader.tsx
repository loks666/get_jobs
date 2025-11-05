"use client"
import { ReactNode } from 'react'

export default function PageHeader({
  icon,
  title,
  subtitle,
  iconClass = 'text-primary',
  accentBgClass = 'bg-primary/10',
}: {
  icon: ReactNode
  title: string
  subtitle?: string
  iconClass?: string
  accentBgClass?: string
}) {
  return (
    <div className="mb-6 animate-in fade-in slide-in-from-bottom-2 duration-500">
      <div className="flex items-center gap-4">
        <div className={`p-3 rounded-xl ${accentBgClass}`}>
          <span className={iconClass}>{icon}</span>
        </div>
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
          {subtitle && <p className="text-muted-foreground mt-1">{subtitle}</p>}
        </div>
      </div>
    </div>
  )
}