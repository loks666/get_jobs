"use client"
import { usePathname } from 'next/navigation'
import { ReactNode, useMemo } from 'react'
import { motion } from 'framer-motion'

export default function ContentArea({ children }: { children: ReactNode }) {
  const pathname = usePathname()

  const accentClass = useMemo(() => {
    switch (pathname) {
      case '/boss': return 'accent-teal'
      case '/liepin': return 'accent-orange'
      case '/51job': return 'accent-amber'
      case '/zhilian': return 'accent-sky'
      default: return ''
    }
  }, [pathname])

  return (
    <main className={`flex-1 ml-64 min-h-screen content-bg ${accentClass}`}>
      <motion.div
        key={pathname}
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35, ease: [0.25, 0.1, 0.25, 1] }}
        className="container py-8 max-w-5xl"
      >
        {children}
      </motion.div>
    </main>
  )
}
