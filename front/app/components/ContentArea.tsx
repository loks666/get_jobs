"use client"
import { usePathname } from 'next/navigation'
import { ReactNode, useMemo } from 'react'

export default function ContentArea({ children }: { children: ReactNode }) {
  const pathname = usePathname()

  const accentClass = useMemo(() => {
    switch (pathname) {
      case '/boss':
        return 'accent-teal'
      case '/liepin':
        return 'accent-orange'
      case '/job51':
        return 'accent-amber'
      case '/zhilian':
        return 'accent-sky'
      default:
        return ''
    }
  }, [pathname])

  return (
    <main className={`flex-1 ml-64 bg-background content-bg ${accentClass}`}>
      <div className="container py-8">
        {children}
      </div>
    </main>
  )
}