"use client"
import { usePathname } from 'next/navigation'
import { ReactNode, useMemo } from 'react'
import { motion } from 'framer-motion'

export default function ContentArea({ children }: { children: ReactNode }) {
  const pathname = usePathname()

  const accentClass = useMemo(() => {
    switch (pathname) {
      case '/boss':
        return 'accent-teal'
      case '/liepin':
        return 'accent-orange'
      case '/51job':
        return 'accent-amber'
      case '/zhilian':
        return 'accent-sky'
      default:
        return ''
    }
  }, [pathname])

  return (
    <main className={`flex-1 ml-64 bg-background dark:bg-blacksection content-bg ${accentClass} min-h-screen`}>
      <motion.div
        key={pathname}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -20 }}
        transition={{ duration: 0.4, ease: "easeInOut" }}
        className="container py-8"
      >
        {children}
      </motion.div>
    </main>
  )
}