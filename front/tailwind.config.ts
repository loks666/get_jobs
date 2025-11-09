import type { Config } from "tailwindcss"

const config: Config = {
  darkMode: ["class"],
  content: [
    "./pages/**/*.{ts,tsx}",
    "./components/**/*.{ts,tsx}",
    "./app/**/*.{ts,tsx}",
    "./src/**/*.{ts,tsx}",
  ],
  prefix: "",
  theme: {
    container: {
      center: true,
      padding: "2rem",
      screens: {
        "2xl": "1400px",
      },
    },
    extend: {
      fontFamily: {
        sans: ['Microsoft YaHei', 'sans-serif'],
      },
      colors: {
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        popover: {
          DEFAULT: "hsl(var(--popover))",
          foreground: "hsl(var(--popover-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        // Solid Next.js 模板颜色
        stroke: "var(--color-stroke)",
        strokedark: "var(--color-strokedark)",
        hoverdark: "var(--color-hoverdark)",
        titlebg: "var(--color-titlebg)",
        titlebg2: "var(--color-titlebg2)",
        titlebgdark: "var(--color-titlebgdark)",
        btndark: "var(--color-btndark)",
        white: "var(--color-white)",
        black: "var(--color-black)",
        blackho: "var(--color-blackho)",
        blacksection: "var(--color-blacksection)",
        meta: "var(--color-meta)",
        waterloo: "var(--color-waterloo)",
        manatee: "var(--color-manatee)",
        alabaster: "var(--color-alabaster)",
        zumthor: "var(--color-zumthor)",
        socialicon: "var(--color-socialicon)",
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
      // Solid Next.js 阴影系统
      boxShadow: {
        'solid-l': '0px 10px 120px 0px rgba(45, 74, 170, 0.1)',
        'solid-2': '0px 2px 10px rgba(122, 135, 167, 0.05)',
        'solid-3': '0px 6px 90px rgba(8, 14, 40, 0.04)',
        'solid-4': '0px 6px 90px rgba(8, 14, 40, 0.1)',
        'solid-5': '0px 8px 24px rgba(45, 74, 170, 0.08)',
        'solid-6': '0px 8px 24px rgba(10, 16, 35, 0.08)',
        'solid-7': '0px 30px 50px rgba(45, 74, 170, 0.1)',
        'solid-8': '0px 12px 120px rgba(45, 74, 170, 0.06)',
        'solid-9': '0px 12px 30px rgba(45, 74, 170, 0.06)',
        'solid-10': '0px 8px 30px rgba(45, 74, 170, 0.06)',
        'solid-11': '0px 6px 20px rgba(45, 74, 170, 0.05)',
        'solid-12': '0px 2px 10px rgba(0, 0, 0, 0.05)',
        'solid-13': '0px 2px 19px rgba(0, 0, 0, 0.05)',
      },
      // Solid Next.js 自定义间距
      spacing: {
        '7.5': '1.875rem',    // 30px
        '12.5': '3.125rem',   // 50px
        '15': '3.75rem',      // 60px
        '17.5': '4.375rem',   // 70px
        '20': '5rem',         // 80px
        '22.5': '5.625rem',   // 90px
        '25': '6.25rem',      // 100px
        '27.5': '6.875rem',   // 110px
        '30': '7.5rem',       // 120px
        '35': '8.75rem',      // 140px
        '40': '10rem',        // 160px
        '46': '11.5rem',      // 184px
      },
      keyframes: {
        "accordion-down": {
          from: { height: "0" },
          to: { height: "var(--radix-accordion-content-height)" },
        },
        "accordion-up": {
          from: { height: "var(--radix-accordion-content-height)" },
          to: { height: "0" },
        },
      },
      animation: {
        "accordion-down": "accordion-down 0.2s ease-out",
        "accordion-up": "accordion-up 0.2s ease-out",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
}

export default config
