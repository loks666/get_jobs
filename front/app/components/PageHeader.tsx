"use client"
import { ReactNode } from 'react'
import { motion } from 'framer-motion'

export default function PageHeader({
  icon,
  title,
  subtitle,
  iconClass = 'text-primary',
  accentBgClass = 'bg-primary/10 dark:bg-primary/20',
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
      initial={{ opacity: 0, y: -20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, ease: "easeOut" }}
      className="mb-8"
    >
      <div className="flex items-center gap-4 p-6 rounded-2xl bg-white/50 dark:bg-blacksection/50 backdrop-blur-sm border border-stroke/50 dark:border-strokedark shadow-solid-3 dark:shadow-none">
        <motion.div
          initial={{ scale: 0, rotate: -180 }}
          animate={{ scale: 1, rotate: 0 }}
          transition={{ delay: 0.2, type: "spring", stiffness: 200 }}
          className={`p-4 rounded-xl ${accentBgClass} shadow-solid-2`}
        >
          <span className={`${iconClass} text-2xl`}>{icon}</span>
        </motion.div>
        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: 0.3, duration: 0.5 }}
          className="flex-1"
        >
          <h1 className="text-3xl font-bold tracking-tight text-black dark:text-white">
            {title}
          </h1>
          {subtitle && (
            <p className="text-waterloo dark:text-manatee mt-1.5 text-base">
              {subtitle}
            </p>
          )}
        </motion.div>
        {actions && (
          <motion.div
            initial={{ opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.4, duration: 0.3 }}
            className="ml-auto flex items-center gap-2"
          >
            {actions}
          </motion.div>
        )}
      </div>
    </motion.div>
  )
}