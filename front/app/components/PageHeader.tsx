"use client"
import { ReactNode } from 'react'
import { motion } from 'framer-motion'

export default function PageHeader({
  icon,
  title,
  subtitle,
  iconClass = 'text-primary',
  accentBgClass = 'bg-primary/8 dark:bg-primary/12',
  actions,
}: {
  icon: ReactNode
  title: string
  subtitle?: string
  iconClass?: string
  accentBgClass?: string
  actions?: ReactNode
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: -12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: [0.25, 0.1, 0.25, 1] }}
      className="mb-6"
    >
      <div className="flex items-center gap-4 px-5 py-4 rounded-xl
        bg-card border border-border/60 shadow-card
        dark:bg-white/[0.03] dark:border-white/[0.06]">
        <div className={`shrink-0 p-3 rounded-xl ${accentBgClass}`}>
          <span className={`${iconClass} text-xl`}>{icon}</span>
        </div>
        <div className="flex-1 min-w-0">
          <h1 className="text-2xl font-bold font-display tracking-tight text-foreground">
            {title}
          </h1>
          {subtitle && (
            <p className="text-sm text-muted-foreground mt-0.5">
              {subtitle}
            </p>
          )}
        </div>
        {actions && (
          <div className="shrink-0 flex items-center gap-2">
            {actions}
          </div>
        )}
      </div>
    </motion.div>
  )
}
