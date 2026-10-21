'use client'

import { useState, useEffect, useRef, useCallback } from 'react'
import { createPortal } from 'react-dom'
import { BiSearch, BiX } from 'react-icons/bi'

export interface OptionItem {
  /** Boss 的选项带自增 id；51job / 智联的选项只有 name + code，因此这里设为可选 */
  id?: number
  code: string
  name: string
}

interface CityMultiSelectProps {
  options: OptionItem[]
  selected: string[]
  onChange: (v: string[]) => void
  placeholder?: string
  searchPlaceholder?: string
}

/**
 * 带搜索框的多选下拉组件。
 *
 * 与 Boss 页面内置的 MultiSelect 的区别：
 * 1. 支持关键字过滤，适用于城市这类选项数量很多（Boss 有 375 个城市）的场景；
 * 2. 显示已选数量，避免选中项过多时把按钮文本撑爆；
 * 3. 提供「清空」入口。
 *
 * selected 保存的是 option.code，与后端 BossConfigController 的 parseListString/toNames 约定一致。
 */
export default function CityMultiSelect({
  options,
  selected,
  onChange,
  placeholder = '请选择',
  searchPlaceholder = '搜索...',
}: CityMultiSelectProps) {
  const [open, setOpen] = useState(false)
  const [mounted, setMounted] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [position, setPosition] = useState({ top: 0, left: 0, width: 0 })
  const wrapperRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setMounted(true)
  }, [])

  const updatePosition = useCallback(() => {
    if (buttonRef.current) {
      const rect = buttonRef.current.getBoundingClientRect()
      setPosition({ top: rect.bottom + 8, left: rect.left, width: rect.width })
    }
  }, [])

  useEffect(() => {
    if (!open) return
    updatePosition()
    const handleUpdate = () => updatePosition()
    window.addEventListener('scroll', handleUpdate, true)
    window.addEventListener('resize', handleUpdate)
    return () => {
      window.removeEventListener('scroll', handleUpdate, true)
      window.removeEventListener('resize', handleUpdate)
    }
  }, [open, updatePosition])

  // 点击外部或按 ESC 关闭
  useEffect(() => {
    if (!open) return
    const handleOutsideClick = (e: MouseEvent) => {
      const target = e.target as Node
      if (!wrapperRef.current?.contains(target) && !dropdownRef.current?.contains(target)) {
        setOpen(false)
      }
    }
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    const timer = setTimeout(() => {
      document.addEventListener('mousedown', handleOutsideClick)
      document.addEventListener('keydown', handleEscape)
    })
    return () => {
      clearTimeout(timer)
      document.removeEventListener('mousedown', handleOutsideClick)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open])

  const selectedNames = options.filter((o) => selected.includes(o.code)).map((o) => o.name)

  const filtered = (() => {
    const kw = keyword.trim()
    if (!kw) return options
    return options.filter((o) => o.name.includes(kw))
  })()

  const toggle = (code: string) => {
    onChange(selected.includes(code) ? selected.filter((c) => c !== code) : [...selected, code])
  }

  return (
    <div className="relative" ref={wrapperRef}>
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex h-10 w-full items-center justify-between rounded-full border border-white/20 bg-white/10 px-4 py-2 text-sm shadow-[inset_0_1px_0_rgba(255,255,255,.25)] transition-all duration-200 hover:bg-white/15 hover:shadow-md focus:outline-none focus:ring-2 focus:ring-cyan-400/40"
      >
        <span className="truncate text-sm">
          {selectedNames.length > 0 ? selectedNames.join('，') : placeholder}
        </span>
        <span className={`ml-2 text-xs text-muted-foreground transition-transform duration-200 ${open ? 'rotate-180' : ''}`}>▼</span>
      </button>

      {open && mounted && createPortal(
        <div
          ref={dropdownRef}
          className="dropdown-panel p-2"
          style={{ top: `${position.top}px`, left: `${position.left}px`, width: `${position.width}px` }}
        >
          <div className="sticky top-0 z-10 -mt-2 -mx-2 mb-2 border-b border-black/10 bg-white/95 px-3 py-2 dark:border-white/10 dark:bg-neutral-900">
            <div className="relative">
              <BiSearch className="absolute left-2 top-1/2 -translate-y-1/2 text-sm text-muted-foreground" />
              <input
                autoFocus
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder={searchPlaceholder}
                className="h-8 w-full rounded-full border border-black/10 bg-black/5 pl-7 pr-7 text-sm outline-none placeholder:text-muted-foreground focus:border-cyan-400/60 dark:border-white/15 dark:bg-white/10"
              />
              {keyword && (
                <button
                  type="button"
                  onClick={() => setKeyword('')}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                >
                  <BiX />
                </button>
              )}
            </div>
            <div className="mt-1.5 flex items-center justify-between text-xs text-muted-foreground">
              <span>已选 {selected.length} 个</span>
              {selected.length > 0 && (
                <button
                  type="button"
                  onClick={() => onChange([])}
                  className="hover:text-foreground"
                >
                  清空
                </button>
              )}
            </div>
          </div>

          {filtered.length === 0 ? (
            <div className="px-3 py-6 text-center text-sm text-muted-foreground">没有匹配的选项</div>
          ) : (
            <div className="flex flex-col gap-1">
              {filtered.map((opt) => {
                const checked = selected.includes(opt.code)
                return (
                  <div
                    key={opt.code}
                    onClick={() => toggle(opt.code)}
                    className={`flex cursor-pointer items-center gap-2.5 rounded-full px-3 py-2 text-sm transition-all ${
                      checked
                        ? 'border border-teal-300/60 bg-gradient-to-r from-teal-500/12 to-cyan-500/12'
                        : 'border border-transparent hover:bg-black/5 dark:hover:bg-white/10'
                    }`}
                  >
                    <span
                      className={`inline-flex h-4 w-4 items-center justify-center rounded-md border transition-all ${
                        checked ? 'border-teal-300/80 bg-teal-400/60' : 'border-white/30 bg-white/10'
                      }`}
                    />
                    <span className="truncate">{opt.name}</span>
                  </div>
                )
              })}
            </div>
          )}
        </div>,
        document.body
      )}
    </div>
  )
}
