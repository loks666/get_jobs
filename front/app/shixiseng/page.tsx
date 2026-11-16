'use client'

import { useState, useEffect, useCallback } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Select } from '@/components/ui/select'
import PageHeader from '@/app/components/PageHeader'
import AnalysisContent from './analysis/AnalysisContent'
import { API_BASE } from '@/lib/api'
import {
  BiPlay,
  BiStop,
  BiSave,
  BiPlus,
  BiX,
  BiCheckCircle,
  BiErrorCircle,
  BiLogOut,
  BiCookie,
  BiBookOpen,
  BiBarChartAlt2,
  BiSliderAlt,
  BiBrain,
  BiShield,
} from 'react-icons/bi'

interface ShixisengConfigData {
  id?: number
  keywords: string[]
  city: string
  enableAi: boolean
}

interface BlacklistItem {
  id: number
  type: number
  name: string
}

const PRESET_KEYWORDS = [
  '产品实习生',
  'AI产品实习生',
  '策略产品实习生',
  '大模型应用实习生',
  'AIGC产品实习生',
  'AI Agent实习生',
  '数据分析实习生',
  '增长运营实习生',
]

const PRESET_CITIES = ['广州', '深圳', '北京', '上海', '杭州', '成都', '全国']

export default function ShixisengPage() {
  const [activeTab, setActiveTab] = useState<'config' | 'analysis'>('config')

  const [config, setConfig] = useState<ShixisengConfigData>({
    keywords: ['产品实习生', 'AI产品实习生'],
    city: '广州',
    enableAi: true,
  })
  const [keywordInput, setKeywordInput] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  // 运行与登录状态
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [isRunning, setIsRunning] = useState(false)
  const [statusMessage, setStatusMessage] = useState<{ type: 'success' | 'error' | 'info'; text: string } | null>(null)

  // AI 个人背景与筛选提示词
  const [aiIntroduce, setAiIntroduce] = useState('')
  const [aiPrompt, setAiPrompt] = useState('')
  const [savingAi, setSavingAi] = useState(false)

  // 共享黑名单（boss_blacklist）
  const [companyBlacklist, setCompanyBlacklist] = useState<BlacklistItem[]>([])
  const [blacklistInput, setBlacklistInput] = useState('')
  const [addingBlacklist, setAddingBlacklist] = useState(false)

  const showMessage = (type: 'success' | 'error' | 'info', text: string) => {
    setStatusMessage({ type, text })
    setTimeout(() => setStatusMessage(null), 5000)
  }

  const loadConfig = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/shixiseng/config`)
      if (res.ok) {
        const data = await res.json()
        const cfg = data.config
        if (cfg) {
          let parsedKeywords: string[] = ['产品实习生']
          if (typeof cfg.keywords === 'string' && cfg.keywords.trim()) {
            try {
              const arr = JSON.parse(cfg.keywords)
              if (Array.isArray(arr) && arr.length > 0) parsedKeywords = arr
            } catch {
              parsedKeywords = cfg.keywords.split(/[,，]/).map((s: string) => s.trim()).filter(Boolean)
            }
          }
          setConfig({
            id: cfg.id,
            keywords: parsedKeywords,
            city: cfg.city || '广州',
            enableAi: cfg.enableAi !== undefined ? Boolean(cfg.enableAi) : true,
          })
        }
      }
    } catch (e) {
      console.error('加载实习僧配置失败:', e)
    } finally {
      setLoading(false)
    }
  }, [])

  const loadAiAndBlacklist = useCallback(async () => {
    try {
      const [aiRes, bossRes] = await Promise.all([
        fetch(`${API_BASE}/api/ai/config`),
        fetch(`${API_BASE}/api/boss/config`),
      ])
      if (aiRes.ok) {
        const aiData = await aiRes.json()
        if (aiData?.data) {
          setAiIntroduce(aiData.data.introduce || '')
          setAiPrompt(aiData.data.prompt || '')
        } else {
          setAiIntroduce(aiData.introduce || '')
          setAiPrompt(aiData.prompt || '')
        }
      }
      if (bossRes.ok) {
        const bossData = await bossRes.json()
        const bl = bossData.blacklists || {}
        const companies: BlacklistItem[] = bl['1'] || bl[1] || []
        setCompanyBlacklist(Array.isArray(companies) ? companies : [])
      }
    } catch (e) {
      console.error('加载AI与黑名单配置失败:', e)
    }
  }, [])

  const refreshLoginAndRunStatus = useCallback(async () => {
    try {
      const [loginRes, runRes] = await Promise.all([
        fetch(`${API_BASE}/api/shixiseng/login-status`),
        fetch(`${API_BASE}/api/shixiseng/status`),
      ])
      if (loginRes.ok) {
        const d = await loginRes.json()
        setIsLoggedIn(Boolean(d.isLoggedIn))
      }
      if (runRes.ok) {
        const d = await runRes.json()
        setIsRunning(Boolean(d.isRunning))
      }
    } catch {
      // ignore polling error
    }
  }, [])

  useEffect(() => {
    loadConfig()
    loadAiAndBlacklist()
    refreshLoginAndRunStatus()

    const es = new EventSource(`${API_BASE}/api/jobs/login-status/stream`)
    es.addEventListener('connected', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        if (typeof data.shixisengLoggedIn === 'boolean') {
          setIsLoggedIn(data.shixisengLoggedIn)
        }
      } catch {}
    })
    es.addEventListener('loginStatusChange', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        if (data.platform === 'shixiseng') {
          setIsLoggedIn(Boolean(data.isLoggedIn))
        }
      } catch {}
    })

    const timer = setInterval(refreshLoginAndRunStatus, 4000)
    return () => {
      es.close()
      clearInterval(timer)
    }
  }, [loadConfig, loadAiAndBlacklist, refreshLoginAndRunStatus])

  const handleSaveConfig = async () => {
    setSaving(true)
    try {
      const payload = {
        id: config.id || 1,
        keywords: JSON.stringify(config.keywords),
        city: config.city.trim() || '广州',
        enableAi: config.enableAi ? 1 : 0,
      }
      const res = await fetch(`${API_BASE}/api/shixiseng/config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (res.ok) {
        showMessage('success', '实习僧投递配置已保存')
        await loadConfig()
      } else {
        showMessage('error', '保存配置失败')
      }
    } catch {
      showMessage('error', '保存配置请求失败')
    } finally {
      setSaving(false)
    }
  }

  const handleSaveAiConfig = async () => {
    setSavingAi(true)
    try {
      const res = await fetch(`${API_BASE}/api/ai/config`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ introduce: aiIntroduce, prompt: aiPrompt }),
      })
      if (res.ok) {
        showMessage('success', 'AI个人画像与筛选准则已更新（全平台同步生效）')
      } else {
        showMessage('error', 'AI配置保存失败')
      }
    } catch {
      showMessage('error', 'AI配置保存失败')
    } finally {
      setSavingAi(false)
    }
  }

  const handleStart = async () => {
    try {
      await handleSaveConfig()
      const res = await fetch(`${API_BASE}/api/shixiseng/start`, { method: 'POST' })
      const data = await res.json()
      if (res.ok && data.success) {
        setIsRunning(true)
        showMessage('success', data.message || '实习僧投递任务已启动')
      } else {
        showMessage('error', data.message || '启动失败')
      }
    } catch {
      showMessage('error', '启动请求失败')
    }
  }

  const handleStop = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/shixiseng/stop`, { method: 'POST' })
      const data = await res.json()
      if (res.ok && data.success) {
        showMessage('info', data.message || '已发送停止信号')
      } else {
        showMessage('error', data.message || '停止失败')
      }
    } catch {
      showMessage('error', '停止请求失败')
    }
  }

  const handleSaveCookie = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/cookie/save?platform=shixiseng`, { method: 'POST' })
      const data = await res.json()
      if (res.ok && data.success !== false) {
        showMessage('success', '实习僧登录 Cookie 已保存至本地数据库')
        refreshLoginAndRunStatus()
      } else {
        showMessage('error', data.message || '保存 Cookie 失败')
      }
    } catch {
      showMessage('error', '保存 Cookie 失败')
    }
  }

  const handleLogout = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/shixiseng/logout`, { method: 'POST' })
      const data = await res.json()
      if (res.ok && data.success) {
        setIsLoggedIn(false)
        showMessage('info', '已退出实习僧登录并清理 Cookie')
      } else {
        showMessage('error', data.message || '退出失败')
      }
    } catch {
      showMessage('error', '退出请求失败')
    }
  }

  const addKeyword = (kw: string) => {
    const clean = kw.trim()
    if (!clean) return
    if (!config.keywords.includes(clean)) {
      setConfig((prev) => ({ ...prev, keywords: [...prev.keywords, clean] }))
    }
    setKeywordInput('')
  }

  const removeKeyword = (kw: string) => {
    setConfig((prev) => ({ ...prev, keywords: prev.keywords.filter((item) => item !== kw) }))
  }

  const handleAddBlacklist = async () => {
    const name = blacklistInput.trim()
    if (!name) return
    setAddingBlacklist(true)
    try {
      const res = await fetch(`${API_BASE}/api/boss/config/blacklist`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: 1, name }),
      })
      if (res.ok) {
        setBlacklistInput('')
        await loadAiAndBlacklist()
        showMessage('success', `已将「${name}」加入全平台公司黑名单`)
      }
    } finally {
      setAddingBlacklist(false)
    }
  }

  const handleRemoveBlacklist = async (id: number) => {
    const res = await fetch(`${API_BASE}/api/boss/config/blacklist/${id}`, { method: 'DELETE' })
    if (res.ok) {
      setCompanyBlacklist((prev) => prev.filter((item) => item.id !== id))
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-4 md:p-6">
      <PageHeader
        title="实习僧自动投递"
        subtitle="基于真实详情 API 与 AI 简历-JD 匹配度筛选的实习僧自动投递引擎"
        icon={<BiBookOpen />}
      />

      {statusMessage && (
        <div
          className={`flex items-center gap-2 rounded-lg border px-4 py-3 text-sm ${
            statusMessage.type === 'success'
              ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
              : statusMessage.type === 'error'
                ? 'border-rose-500/40 bg-rose-500/10 text-rose-700 dark:text-rose-300'
                : 'border-sky-500/40 bg-sky-500/10 text-sky-700 dark:text-sky-300'
          }`}
        >
          {statusMessage.type === 'success' ? <BiCheckCircle className="text-lg" /> : <BiErrorCircle className="text-lg" />}
          <span>{statusMessage.text}</span>
        </div>
      )}

      {/* 顶部控制与登录状态卡片 */}
      <Card>
        <CardContent className="flex flex-wrap items-center justify-between gap-4 pt-6">
          <div className="flex flex-wrap items-center gap-3">
            <span
              className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${
                isLoggedIn ? 'bg-emerald-500/15 text-emerald-600' : 'bg-amber-500/15 text-amber-600'
              }`}
            >
              <span className={`h-2 w-2 rounded-full ${isLoggedIn ? 'bg-emerald-500' : 'bg-amber-500'}`} />
              {isLoggedIn ? '实习僧已登录' : '未登录（请在 Chrome 弹窗完成登录）'}
            </span>
            <span
              className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${
                isRunning ? 'bg-sky-500/15 text-sky-600' : 'bg-slate-500/15 text-slate-500'
              }`}
            >
              {isRunning ? '投递任务运行中...' : '当前空闲'}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Button variant="outline" size="sm" onClick={handleSaveCookie} className="gap-1.5">
              <BiCookie /> 保存 Cookie
            </Button>
            {isLoggedIn && (
              <Button variant="outline" size="sm" onClick={handleLogout} className="gap-1.5 text-rose-600 hover:text-rose-700">
                <BiLogOut /> 退出登录
              </Button>
            )}
            <Button variant="outline" size="sm" onClick={handleSaveConfig} disabled={saving} className="gap-1.5">
              <BiSave /> {saving ? '保存中...' : '保存配置'}
            </Button>
            {!isRunning ? (
              <Button size="sm" onClick={handleStart} disabled={loading} className="gap-1.5 bg-sky-600 text-white hover:bg-sky-700">
                <BiPlay /> 启动投递
              </Button>
            ) : (
              <Button size="sm" variant="destructive" onClick={handleStop} className="gap-1.5">
                <BiStop /> 停止投递
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Tabs */}
      <div className="flex gap-2 border-b pb-2">
        <Button
          variant={activeTab === 'config' ? 'default' : 'ghost'}
          size="sm"
          onClick={() => setActiveTab('config')}
          className="gap-1.5"
        >
          <BiSliderAlt /> 投递策略配置
        </Button>
        <Button
          variant={activeTab === 'analysis' ? 'default' : 'ghost'}
          size="sm"
          onClick={() => setActiveTab('analysis')}
          className="gap-1.5"
        >
          <BiBarChartAlt2 /> 投递数据分析
        </Button>
      </div>

      {activeTab === 'config' ? (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          {/* 左栏：关键词 + 城市 */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">检索条件与关键词</CardTitle>
              <CardDescription>配置搜索关键词（支持多个轮询）及期望实习城市</CardDescription>
            </CardHeader>
            <CardContent className="space-y-5">
              <div className="space-y-2">
                <Label>期望城市</Label>
                <div className="grid grid-cols-2 gap-2">
                  <Select
                    value={PRESET_CITIES.includes(config.city) ? config.city : '__custom__'}
                    onChange={(e) => {
                      if (e.target.value !== '__custom__') {
                        setConfig((prev) => ({ ...prev, city: e.target.value }))
                      }
                    }}
                  >
                    {PRESET_CITIES.map((c) => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                    <option value="__custom__">自定义城市</option>
                  </Select>
                  <Input
                    value={config.city}
                    onChange={(e) => setConfig((prev) => ({ ...prev, city: e.target.value }))}
                    placeholder="输入城市名，如 广州"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label>搜索关键词列表（{config.keywords.length} 个）</Label>
                <div className="flex gap-2">
                  <Input
                    value={keywordInput}
                    onChange={(e) => setKeywordInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault()
                        addKeyword(keywordInput)
                      }
                    }}
                    placeholder="输入岗位关键词后回车，例如 AI产品实习生"
                  />
                  <Button type="button" variant="outline" onClick={() => addKeyword(keywordInput)}>
                    <BiPlus /> 添加
                  </Button>
                </div>

                <div className="flex flex-wrap gap-2 pt-2">
                  {config.keywords.map((kw) => (
                    <span
                      key={kw}
                      className="inline-flex items-center gap-1 rounded-full bg-sky-500/15 px-3 py-1 text-xs font-medium text-sky-700 dark:text-sky-300"
                    >
                      {kw}
                      <button type="button" onClick={() => removeKeyword(kw)} className="hover:text-rose-500">
                        <BiX />
                      </button>
                    </span>
                  ))}
                </div>

                <div className="pt-2">
                  <p className="mb-1.5 text-xs text-muted-foreground">推荐产品/AI实习关键词（点击快选）：</p>
                  <div className="flex flex-wrap gap-1.5">
                    {PRESET_KEYWORDS.map((preset) => (
                      <button
                        key={preset}
                        type="button"
                        onClick={() => addKeyword(preset)}
                        className="rounded-md border px-2 py-1 text-xs text-muted-foreground transition hover:border-sky-500 hover:text-sky-600"
                      >
                        + {preset}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* 右栏：AI 筛选 + 黑名单 */}
          <div className="space-y-6">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-3">
                <div>
                  <CardTitle className="flex items-center gap-2 text-base">
                    <BiBrain className="text-sky-500" /> AI 智能匹配过滤
                  </CardTitle>
                  <CardDescription>开启后调用大模型评估 JD 与个人背景是否匹配，自动过滤硬性门槛不符的岗位</CardDescription>
                </div>
                <button
                  type="button"
                  role="switch"
                  aria-checked={config.enableAi}
                  onClick={() => setConfig((prev) => ({ ...prev, enableAi: !prev.enableAi }))}
                  className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
                    config.enableAi ? 'bg-sky-600' : 'bg-slate-300 dark:bg-slate-700'
                  }`}
                >
                  <span
                    className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
                      config.enableAi ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-1.5">
                  <Label className="text-xs">个人简历核心背景（AI 评估依据）</Label>
                  <Textarea
                    rows={3}
                    value={aiIntroduce}
                    onChange={(e) => setAiIntroduce(e.target.value)}
                    placeholder="简要描述你的年级、专业、实习经历、到岗时间与硬技能..."
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs">AI 筛选判定准则 Prompt（参考 Boss 直聘筛选逻辑）</Label>
                  <Textarea
                    rows={3}
                    value={aiPrompt}
                    onChange={(e) => setAiPrompt(e.target.value)}
                    placeholder="定义何种岗位放行、何种条件拦截（如要求全职、硬性硕士、到岗时长不符等）..."
                  />
                </div>
                <div className="flex justify-end">
                  <Button variant="outline" size="sm" onClick={handleSaveAiConfig} disabled={savingAi}>
                    {savingAi ? '保存中...' : '保存 AI 匹配准则'}
                  </Button>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <BiShield className="text-rose-500" /> 公司黑名单（全平台共享）
                </CardTitle>
                <CardDescription>包含以下关键词的公司将被自动跳过</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex gap-2">
                  <Input
                    value={blacklistInput}
                    onChange={(e) => setBlacklistInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault()
                        handleAddBlacklist()
                      }
                    }}
                    placeholder="输入需屏蔽的公司关键词，如 外包"
                  />
                  <Button type="button" variant="outline" onClick={handleAddBlacklist} disabled={addingBlacklist}>
                    添加
                  </Button>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {companyBlacklist.map((item) => (
                    <span
                      key={item.id}
                      className="inline-flex items-center gap-1 rounded-full bg-rose-500/10 px-2.5 py-1 text-xs text-rose-600"
                    >
                      {item.name}
                      <button type="button" onClick={() => handleRemoveBlacklist(item.id)} className="hover:text-rose-800">
                        <BiX />
                      </button>
                    </span>
                  ))}
                  {companyBlacklist.length === 0 && (
                    <span className="text-xs text-muted-foreground">暂无屏蔽公司关键词</span>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      ) : (
        <AnalysisContent />
      )}
    </div>
  )
}
