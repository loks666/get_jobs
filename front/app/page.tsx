import { redirect } from 'next/navigation'

export default function HomeRedirect() {
  // 根路径重定向到环境配置页
  redirect('/env-config')
}