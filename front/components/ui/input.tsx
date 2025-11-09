import * as React from "react"

import { cn } from "@/lib/utils"

const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        type={type}
        className={cn(
          // 尺寸与排版改为胶囊
          "flex h-10 w-full rounded-full px-4 py-2 text-sm",
          // 玻璃风格与清晰边界（与 Select 保持一致）
          "border border-white/40 bg-white/5 shadow-[inset_0_1px_0_rgba(255,255,255,.25)]",
          // 交互态更柔和
          "transition-all duration-200 hover:bg-white/10 hover:shadow-md",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400/40 focus-visible:border-emerald-300/60",
          // 文件输入与占位
          "file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground",
          "placeholder:text-muted-foreground",
          // 禁用态
          "disabled:cursor-not-allowed disabled:opacity-50",
          // 覆盖浏览器自动填充样式
          "[&:-webkit-autofill]:bg-white/5 [&:-webkit-autofill]:shadow-[inset_0_0_0_1000px_rgba(255,255,255,0.05)]",
          "[&:-webkit-autofill:hover]:bg-white/10 [&:-webkit-autofill:hover]:shadow-[inset_0_0_0_1000px_rgba(255,255,255,0.1)]",
          "[&:-webkit-autofill:focus]:bg-white/5 [&:-webkit-autofill:focus]:shadow-[inset_0_0_0_1000px_rgba(255,255,255,0.05)]",
          className
        )}
        ref={ref}
        {...props}
      />
    )
  }
)
Input.displayName = "Input"

export { Input }
