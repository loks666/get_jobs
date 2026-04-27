import * as React from "react"
import { createPortal } from "react-dom"
import { cn } from "@/lib/utils"

type OptionItem = { value: string; label: React.ReactNode }

export interface SelectProps {
  value?: string
  onChange?: (e: { target: { value: string } }) => void
  placeholder?: string
  className?: string
  id?: string
  disabled?: boolean
  children?: React.ReactNode
}

const Select = React.forwardRef<HTMLDivElement, SelectProps>(
  ({ className, children, value, onChange, placeholder, disabled, id, ...props }, ref) => {
    const [open, setOpen] = React.useState(false)
    const [mounted, setMounted] = React.useState(false)
    const wrapperRef = React.useRef<HTMLDivElement>(null)
    const buttonRef = React.useRef<HTMLButtonElement>(null)
    const dropdownRef = React.useRef<HTMLDivElement>(null)
    const [dropdownPosition, setDropdownPosition] = React.useState({ top: 0, left: 0, width: 0 })

    React.useEffect(() => { setMounted(true) }, [])

    const options = React.useMemo<OptionItem[]>(() => {
      return React.Children.toArray(children)
        .filter((c) => React.isValidElement(c) && (c as any).type === 'option')
        .map((c: any) => ({ value: String(c.props.value ?? c.props.children), label: c.props.children }))
    }, [children])

    const selected = options.find((o) => String(value ?? '') === String(o.value))
    const emitChange = (val: string) => onChange?.({ target: { value: val } } as any)

    const updatePosition = React.useCallback(() => {
      if (buttonRef.current) {
        const rect = buttonRef.current.getBoundingClientRect()
        setDropdownPosition({ top: rect.bottom + 6, left: rect.left, width: rect.width })
      }
    }, [])

    React.useEffect(() => {
      if (open) {
        updatePosition()
        const handleUpdate = () => updatePosition()
        window.addEventListener('scroll', handleUpdate, true)
        window.addEventListener('resize', handleUpdate)
        return () => {
          window.removeEventListener('scroll', handleUpdate, true)
          window.removeEventListener('resize', handleUpdate)
        }
      }
    }, [open, updatePosition])

    React.useEffect(() => {
      const handleClickOutside = (event: MouseEvent) => {
        const target = event.target as Node
        if (!wrapperRef.current?.contains(target) && !dropdownRef.current?.contains(target)) {
          setOpen(false)
        }
      }
      const handleEscape = (event: KeyboardEvent) => {
        if (event.key === 'Escape') setOpen(false)
      }
      if (open) {
        setTimeout(() => {
          document.addEventListener('mousedown', handleClickOutside)
          document.addEventListener('keydown', handleEscape)
        }, 0)
      }
      return () => {
        document.removeEventListener('mousedown', handleClickOutside)
        document.removeEventListener('keydown', handleEscape)
      }
    }, [open])

    return (
      <div ref={ref} {...props}>
        <div ref={wrapperRef} className="relative">
          <button
            ref={buttonRef}
            id={id as string}
            type="button"
            disabled={disabled}
            onClick={() => setOpen((v) => !v)}
            className={cn(
              "flex h-10 w-full rounded-lg px-3.5 py-2 text-sm",
              "border border-input bg-background",
              "transition-all duration-200",
              "hover:border-foreground/20",
              disabled
                ? "cursor-not-allowed opacity-50"
                : "focus:outline-none focus:ring-2 focus:ring-ring/30 focus:border-primary/40",
              "bg-[url('data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 24 24%22 fill=%22none%22 stroke=%22%2394a3b8%22 stroke-width=%222%22><path d=%22M6 9l6 6 6-6%22/></svg>')] bg-no-repeat bg-[length:16px_16px] bg-[position:right_12px_center]",
              className
            )}
          >
            <span className={`truncate text-sm ${!selected ? 'text-muted-foreground/70' : ''}`}>
              {selected ? selected.label : (placeholder ?? '')}
            </span>
          </button>

          {open && mounted && createPortal(
            <div
              ref={dropdownRef}
              className="dropdown-panel"
              style={{
                top: `${dropdownPosition.top}px`,
                left: `${dropdownPosition.left}px`,
                width: `${dropdownPosition.width}px`,
              }}
            >
              <ul className="py-1">
                {options.map((o) => {
                  const active = String(value ?? '') === String(o.value)
                  return (
                    <li
                      key={String(o.value)}
                      className={cn(
                        "flex items-center gap-3 px-3 py-2 cursor-pointer transition-colors duration-150",
                        active
                          ? "bg-primary/[0.08] text-foreground"
                          : "text-foreground/80 hover:bg-accent/10"
                      )}
                      onClick={() => {
                        emitChange(String(o.value))
                        setOpen(false)
                      }}
                    >
                      <span className={cn(
                        "inline-flex h-4 w-4 items-center justify-center rounded border transition-colors",
                        active
                          ? "bg-primary/20 border-primary/40"
                          : "border-border bg-background"
                      )}>
                        {active && <span className="h-1.5 w-1.5 rounded-sm bg-primary" />}
                      </span>
                      <span className="text-sm truncate">{o.label}</span>
                    </li>
                  )
                })}
              </ul>
            </div>,
            document.body
          )}
        </div>
      </div>
    )
  }
)
Select.displayName = "Select"

export { Select }
