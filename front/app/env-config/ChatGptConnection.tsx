'use client'

import { useCallback, useEffect, useState } from 'react'
import { API_BASE } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'

type AccountStatus = {
  available: boolean
  loggedIn: boolean
  email?: string
  planType?: string
  pending: boolean
  message?: string
}
type Model = { id: string; name: string }

async function request(path: string, method = 'GET') {
  const response = await fetch(`${API_BASE}/api/chatgpt/${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    signal: AbortSignal.timeout(40000),
  })
  const result = await response.json()
  if (!response.ok || !result.success) throw new Error(result.message || 'ChatGPT 请求失败')
  return result.data
}

export default function ChatGptConnection({ model, onModelChange }: { model: string; onModelChange: (model: string) => void }) {
  const [status, setStatus] = useState<AccountStatus | null>(null)
  const [models, setModels] = useState<Model[]>([])
  const [authUrl, setAuthUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [polling, setPolling] = useState(false)
  const [error, setError] = useState('')

  const refresh = useCallback(async () => {
    const account: AccountStatus = await request('status')
    setStatus(account)
    if (account.loggedIn || !account.pending) {
      setPolling(false)
      setAuthUrl('')
    }
    return account
  }, [])

  useEffect(() => {
    let active = true
    request('status').then((account: AccountStatus) => {
      if (active) { setStatus(account); setPolling(account.pending) }
    }).catch(e => { if (active) setError(e.message) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!polling) return
    let active = true
    let timer: ReturnType<typeof setTimeout>
    const poll = async () => {
      try {
        const account: AccountStatus = await request('status')
        if (!active) return
        setStatus(account)
        if (account.loggedIn || !account.pending) { setPolling(false); setAuthUrl(''); return }
        timer = setTimeout(poll, 3000)
      } catch (e) {
        if (active) { setError(e instanceof Error ? e.message : '无法查询登录状态'); setPolling(false) }
      }
    }
    timer = setTimeout(poll, 3000)
    return () => { active = false; clearTimeout(timer) }
  }, [polling])

  useEffect(() => {
    let active = true
    if (status?.loggedIn) {
      request('models').then(data => { if (active) setModels(data) })
        .catch(e => { if (active) setError(e.message) })
    } else { setModels([]) }
    return () => { active = false }
  }, [status?.loggedIn])

  const action = async (name: 'login' | 'logout' | 'cancel' | 'status') => {
    setBusy(true)
    setError('')
    try {
      if (name === 'login') {
        const result = await request('login', 'POST')
        const url = new URL(result.authUrl)
        if (url.protocol !== 'https:' || url.hostname !== 'auth.openai.com') throw new Error('登录地址异常，请检查 Codex 版本')
        setAuthUrl(url.href)
        setPolling(true)
        setStatus(previous => previous ? { ...previous, pending: true, message: '' } : null)
      } else {
        if (name !== 'status') await request(name, 'POST')
        await refresh()
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '操作失败')
    } finally { setBusy(false) }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        使用 ChatGPT 账号授权，无需 API Key。模型和可用额度由账号的 Codex 权限决定。
        登录信息仅用于本应用；岗位描述和你配置的个人介绍会发送给 OpenAI 生成回复。
      </p>
      <div className="rounded-lg border p-4 space-y-3">
        <p role="status" className="text-sm">
          {!status ? '正在检查登录状态…' : status.loggedIn
            ? `已登录：${status.email || 'ChatGPT'}${status.planType ? ` · ${status.planType}` : ''}`
            : status.pending ? '等待浏览器授权…' : '尚未登录 ChatGPT'}
        </p>
        {status?.message && <p className="text-sm text-muted-foreground">{status.message}</p>}
        <div className="flex flex-wrap gap-2">
          {!status?.loggedIn && <Button type="button" disabled={busy || polling} onClick={() => action('login')}>登录 ChatGPT</Button>}
          {status?.loggedIn && <Button type="button" variant="outline" disabled={busy} onClick={() => action('logout')}>退出登录</Button>}
          {(polling || status?.pending) && <Button type="button" variant="outline" disabled={busy} onClick={() => action('cancel')}>取消登录</Button>}
          <Button type="button" variant="outline" disabled={busy} onClick={() => action('status')}>刷新状态</Button>
        </div>
        {authUrl && <p className="text-sm">
          <a className="text-primary underline" href={authUrl} target="_blank" rel="noopener noreferrer">打开 OpenAI 登录页面</a>
          <span className="text-muted-foreground">，完成授权后此处会自动更新。</span>
        </p>}
        {error && <p role="alert" className="text-sm text-red-500">{error}</p>}
      </div>
      <div className="space-y-2">
        <Label htmlFor="chatGptModel">ChatGPT 模型</Label>
        <select id="chatGptModel" value={model} onChange={e => onModelChange(e.target.value)}
          className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm">
          <option value="">使用账号默认模型</option>
          {model && !models.some(item => item.id === model) && <option value={model}>{model}（已保存）</option>}
          {models.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>
        <p className="text-xs text-muted-foreground">登录后加载模型列表。更改登录方式和模型后，点击页面顶部“保存配置”。</p>
      </div>
    </div>
  )
}
