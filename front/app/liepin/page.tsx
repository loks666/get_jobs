'use client'

import { useState, useEffect } from 'react'
import { createSSEWithBackoff } from '@/lib/sse'
import { BiSearch, BiSave, BiTargetLock, BiMap, BiMoney, BiTime, BiBookmark, BiBarChart, BiPlay, BiStop, BiLogOut, BiBriefcase, BiMessageDetail, BiImage, BiPlus, BiTrash, BiBuilding, BiShieldQuarter, BiBot } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import AnalysisContent from '@/app/liepin/analysis/AnalysisContent'
import PageHeader from '@/app/components/PageHeader'
import { API_BASE } from '@/lib/api'

interface LiepinConfig {
  id?: number
  keywords?: string
  city?: string
  salaryCode?: string
}

interface LiepinOption {
  id: number
  type: string
  name: string
  code: string
}

interface LiepinOptions {
  city: LiepinOption[]
}

interface BlacklistItem {
  id: number
  value: string
  type: string
}

export default function LiepinPage() {
  const [config, setConfig] = useState<LiepinConfig>({
    keywords: '',
    city: '',
    salaryCode: '',
  })
  const [options, setOptions] = useState<LiepinOptions>({
    city: [],
  })
  const [loading, setLoading] = useState(true)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)
  const [isCustomCity, setIsCustomCity] = useState(false) // 是否手动输入城市
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [isDelivering, setIsDelivering] = useState(false)
  const [checkingLogin, setCheckingLogin] = useState(true)
  const [showLogoutDialog, setShowLogoutDialog] = useState(false)
  const [showLogoutResultDialog, setShowLogoutResultDialog] = useState(false)
  const [logoutResult, setLogoutResult] = useState<{ success: boolean; message: string } | null>(null)
  const [sharedBossConfig, setSharedBossConfig] = useState<Record<string, unknown> | null>(null)
  const [enableAI, setEnableAI] = useState(true)
  const [sayHi, setSayHi] = useState('')
  const [sendImgResume, setSendImgResume] = useState(true)
  const [blacklist, setBlacklist] = useState<BlacklistItem[]>([])
  const [newBlacklistKeyword, setNewBlacklistKeyword] = useState('')
  const [blacklistType, setBlacklistType] = useState('job')
  const [aiIntroduce, setAiIntroduce] = useState('')
  const [aiPrompt, setAiPrompt] = useState('')
  const [showAiAdvanced, setShowAiAdvanced] = useState(false)

  useEffect(() => {
    fetchAllData()
    fetch(`${API_BASE}/api/liepin/status`)
      .then((res) => (res.ok ? res.json() : null))
      .then((statusData) => {
        if (statusData && typeof statusData.isRunning === 'boolean') {
          setIsDelivering(statusData.isRunning)
        }
      })
      .catch((err) => console.warn('[Liepin] 获取任务状态失败:', err))

    // 确保在客户端环境且 EventSource 可用
    if (typeof window === 'undefined' || typeof EventSource === 'undefined') {
      console.warn('EventSource 不可用，无法连接SSE')
      setCheckingLogin(false)
      return
    }

    const client = createSSEWithBackoff(`${API_BASE}/api/jobs/login-status/stream`, {
      onOpen: () => {
        console.log('[SSE] 连接已打开')
      },
      onError: (e, attempt, delay) => {
        console.warn(`[SSE] 连接错误，准备第${attempt}次重连，延迟 ${delay}ms`, e)
        setCheckingLogin(false)
      },
      listeners: [
        {
          name: 'connected',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              setIsLoggedIn(data.liepinLoggedIn || false)
              setCheckingLogin(false)
            } catch (error) {
              console.error('[SSE] 解析连接消息失败:', error)
            }
          },
        },
        {
          name: 'login-status',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              if (data.platform === 'liepin') {
                setIsLoggedIn(data.isLoggedIn)
                setCheckingLogin(false)
              }
            } catch (error) {
              console.error('[SSE] 解析登录状态消息失败:', error)
            }
          },
        },
        { name: 'ping', handler: () => {} },
      ],
    })

    return () => {
      client.close()
    }
  }, [])

  // 将数据库中的 JSON 数组字符串转换为逗号分隔的可读字符串
  const parseKeywordsFromDb = (raw?: string): string => {
    if (!raw) return ''
    const t = raw.trim()
    if (t.startsWith('[') && t.endsWith(']')) {
      try {
        const arr = JSON.parse(t)
        if (Array.isArray(arr)) return arr.filter(Boolean).join(', ')
      } catch (e) {
        console.warn('解析关键词JSON失败，使用原值:', e)
      }
    }
    // 兼容逗号或中文逗号分隔的原始字符串
    return t.replace(/，/g, ',')
  }

  // 将输入的逗号分隔字符串转换为 JSON 数组字符串保存到数据库
  const serializeKeywordsForDb = (display?: string): string => {
    const raw = (display || '').trim()
    if (!raw) return '[]'
    const norm = raw.replace(/，/g, ',')
    const tokens = norm
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0)
    return JSON.stringify(tokens)
  }

  const fetchAllData = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/liepin/config`)
      const data = await response.json()

      console.log('Fetched liepin data:', data)

      if (data.config) {
        const normalized = { ...data.config }
        normalized.keywords = parseKeywordsFromDb(data.config.keywords)
        setConfig(normalized)
        // 检查当前城市是否在选项列表中
        if (data.options?.city && data.config.city) {
          const cityExists = data.options.city.some((c: LiepinOption) => c.name === data.config.city || c.code === data.config.city)
          setIsCustomCity(!cityExists)
        }
      }
      if (data.options) {
        setOptions(data.options)
      }

      try {
        const bossRes = await fetch(`${API_BASE}/api/boss/config`)
        const bossData = await bossRes.json()
        if (bossData?.config) {
          setSharedBossConfig(bossData.config)
          setEnableAI(Boolean(bossData.config.enableAI))
          setSayHi(bossData.config.sayHi || '')
          setSendImgResume(Boolean(bossData.config.sendImgResume))
        }
        if (bossData?.blacklist) {
          setBlacklist(
            bossData.blacklist.map((item: BlacklistItem) => ({
              ...item,
              type: item.type === 'boss' ? 'company' : item.type,
            }))
          )
        }
      } catch (e) {
        console.warn('获取共享AI与招呼语配置失败:', e)
      }

      try {
        const aiRes = await fetch(`${API_BASE}/api/ai/config`)
        const aiJson = await aiRes.json()
        if (aiJson?.data) {
          setAiIntroduce(aiJson.data.introduce || '')
          setAiPrompt(aiJson.data.prompt || '')
        }
      } catch (e) {
        console.warn('获取AI画像与Prompt失败:', e)
      }
    } catch (error) {
      console.error('Failed to fetch liepin data:', error)
    } finally {
      setLoading(false)
    }
  }

  const syncSharedBossConfig = async (nextEnableAI: boolean, nextSayHi: string) => {
    if (!sharedBossConfig) return
    try {
      const res = await fetch(`${API_BASE}/api/boss/config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...sharedBossConfig,
          enableAI: nextEnableAI ? 1 : 0,
          sayHi: nextSayHi,
          sendImgResume: sendImgResume ? 1 : 0,
        }),
      })
      if (res.ok) {
        setSharedBossConfig({
          ...sharedBossConfig,
          enableAI: nextEnableAI ? 1 : 0,
          sayHi: nextSayHi,
          sendImgResume: sendImgResume ? 1 : 0,
        })
      }
    } catch (e) {
      console.warn('自动保存AI与招呼语配置失败:', e)
    }
  }

  const saveAiProfile = async (nextIntroduce: string, nextPrompt: string) => {
    try {
      await fetch(`${API_BASE}/api/ai/config`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ introduce: nextIntroduce, prompt: nextPrompt }),
      })
    } catch (e) {
      console.warn('保存AI画像与Prompt失败:', e)
    }
  }

  const handleAddBlacklist = async () => {
    if (!newBlacklistKeyword.trim()) return
    try {
      const response = await fetch(`${API_BASE}/api/boss/config/blacklist`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ value: newBlacklistKeyword.trim(), type: blacklistType }),
      })
      if (response.ok) {
        setNewBlacklistKeyword('')
        fetchAllData()
      }
    } catch (e) {
      console.error('添加黑名单失败:', e)
    }
  }

  const handleDeleteBlacklist = async (id: number) => {
    try {
      const response = await fetch(`${API_BASE}/api/boss/config/blacklist/${id}`, { method: 'DELETE' })
      if (response.ok) {
        fetchAllData()
      }
    } catch (e) {
      console.error('删除黑名单失败:', e)
    }
  }

  const togglePresetKeyword = (kw: string) => {
    const current = (config.keywords || '')
      .replace(/，/g, ',')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
    const exists = current.includes(kw)
    const next = exists ? current.filter((item) => item !== kw) : [...current, kw]
    setConfig({ ...config, keywords: next.join(', ') })
  }

  const handleSave = async () => {
    try {
      const payload = { ...config, keywords: serializeKeywordsForDb(config.keywords) }
      const response = await fetch(`${API_BASE}/api/liepin/config`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      })

      if (response.ok) {
        if (sharedBossConfig) {
          try {
            await fetch(`${API_BASE}/api/boss/config`, {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                ...sharedBossConfig,
                enableAI: enableAI ? 1 : 0,
                sayHi,
                sendImgResume: sendImgResume ? 1 : 0,
              }),
            })
          } catch (e) {
            console.warn('同步保存AI与招呼语配置失败:', e)
          }
        }
        // 统一保存 Cookie（Liepin）
        try {
          await fetch(`${API_BASE}/api/cookie/save?platform=liepin`, { method: 'POST' })
        } catch (e) {
          console.warn('保存 Cookie 失败（Liepin）:', e)
        }

        fetchAllData()
        setSaveResult({ success: true, message: '保存成功，配置与Cookie已更新。' })
        setShowSaveDialog(true)
      } else {
        console.warn('保存失败：后端返回非 2xx 状态')
        setSaveResult({ success: false, message: '保存失败：后端返回异常状态。' })
        setShowSaveDialog(true)
      }
    } catch (error) {
      console.error('Failed to save config:', error)
      setSaveResult({ success: false, message: '保存失败：网络或服务异常。' })
      setShowSaveDialog(true)
    }
  }

  const handleStartDelivery = async () => {
    try {
      setIsDelivering(true)
      const response = await fetch(`${API_BASE}/api/liepin/start`, {
        method: 'POST',
      })
      const data = await response.json()

      if (data.success) {
        // 启动成功：不弹框
      } else {
        // 启动失败：不弹框
        console.warn('启动失败：', data.message)
        setIsDelivering(false)
      }
    } catch (error) {
      console.error('Failed to start delivery:', error)
      // 启动失败：不弹框
      setIsDelivering(false)
    }
  }

  const handleStopDelivery = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/liepin/stop`, {
        method: 'POST',
      })
      const data = await response.json()

      if (data.success) {
        // 停止成功：不弹框
        setIsDelivering(false)
      } else {
        // 停止失败：不弹框
        console.warn('停止失败：', data.message)
      }
    } catch (error) {
      console.error('Failed to stop delivery:', error)
      // 停止失败：不弹框
    }
  }

  const triggerLogout = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/liepin/logout`, { method: 'POST' })
      const data = await response.json()
      if (data.success) {
        setIsLoggedIn(false)
        setIsDelivering(false)
        console.info('已退出登录，数据库Cookie已置空')
        setLogoutResult({ success: true, message: '已退出登录，Cookie已清空。' })
        setShowLogoutResultDialog(true)
      } else {
        console.warn('退出登录失败：', data.message)
        setLogoutResult({ success: false, message: `退出登录失败：${data.message || '服务返回异常。'}` })
        setShowLogoutResultDialog(true)
      }
    } catch (error) {
      console.error('Failed to logout:', error)
      setLogoutResult({ success: false, message: '退出登录失败：网络或服务异常。' })
      setShowLogoutResultDialog(true)
    }
  }

  if (loading) {
    return <div className="flex items-center justify-center h-screen">加载中...</div>
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiSearch className="text-2xl" />}
        title="猎聘配置"
        subtitle="配置猎聘平台的求职参数"
        iconClass="text-white"
        accentBgClass="bg-purple-500"
        actions={
          <div className="flex items-center gap-2">
            {checkingLogin ? (
              <Button size="sm" disabled className="rounded-full bg-gray-300 text-gray-600 cursor-not-allowed px-4 shadow">
                <BiPlay className="mr-1" /> 检查登录中...
              </Button>
            ) : !isLoggedIn ? (
              <Button size="sm" disabled className="rounded-full bg-gray-300 text-gray-600 cursor-not-allowed px-4 shadow">
                <BiPlay className="mr-1" /> 请先登录猎聘
              </Button>
            ) : isDelivering ? (
              <Button onClick={handleStopDelivery} size="sm" className="rounded-full bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
                <BiStop className="mr-1" /> 停止投递
              </Button>
            ) : (
              <Button onClick={handleStartDelivery} size="sm" className="rounded-full bg-gradient-to-r from-teal-500 to-green-500 hover:from-teal-600 hover:to-green-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
                <BiPlay className="mr-1" /> 开始投递
              </Button>
            )}
            <Button onClick={() => setShowLogoutDialog(true)} size="sm" className="rounded-full bg-gradient-to-r from-red-500 to-pink-500 hover:from-red-600 hover:to-pink-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
              <BiLogOut className="mr-1" /> 退出登录
            </Button>
            <Button onClick={handleSave} size="sm" className="rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-600 hover:to-indigo-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
              <BiSave className="mr-1" /> 保存配置
            </Button>
          </div>
        }
      />

      <Tabs defaultValue="config" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="config">平台配置</TabsTrigger>
          <TabsTrigger value="analytics">投递分析</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="space-y-6 mt-6">
        {/* 平台说明 */}
        <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiBriefcase className="text-primary" />
              猎聘平台说明
            </CardTitle>
            <CardDescription>登录与投递操作提示</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <p className="text-sm text-muted-foreground">请在浏览器标签页中登录 猎聘 平台，登录成功后系统会自动检测登录状态。</p>
              <p className="text-sm text-muted-foreground">登录成功后，点击“开始投递”按钮启动自动投递任务。</p>
              <p className="text-sm text-muted-foreground">点击“保存配置”按钮可手动保存当前登录相关信息到数据库。</p>
            </div>
          </CardContent>
        </Card>

        {/* 搜索配置 */}
        <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiSearch className="text-primary" />
              搜索配置
            </CardTitle>
            <CardDescription>设置职位搜索关键词和筛选条件</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="keywords">搜索关键词</Label>
                <Input
                  id="keywords"
                  value={config.keywords || ''}
                  onChange={(e) => setConfig({ ...config, keywords: e.target.value })}
                  placeholder="例如：AI产品实习生, 产品经理实习生, 产品实习生"
                />
                <div className="flex flex-wrap gap-1.5 pt-1">
                  {['AI产品实习生', '产品经理实习生', '产品实习生', '策略产品实习生', '产品助理实习生', '产品运营实习生'].map((kw) => {
                    const active = (config.keywords || '').split(/[,，]/).map((s) => s.trim()).includes(kw)
                    return (
                      <button
                        key={kw}
                        type="button"
                        onClick={() => togglePresetKeyword(kw)}
                        className={`text-xs px-2.5 py-1 rounded-full border transition-colors ${
                          active
                            ? 'bg-purple-600 text-white border-purple-600'
                            : 'bg-background text-muted-foreground hover:border-purple-400'
                        }`}
                      >
                        {active ? '✓ ' : '+ '}
                        {kw}
                      </button>
                    )
                  })}
                </div>
                <p className="text-xs text-muted-foreground">关键词可多选，使用英文逗号分隔，也可点击上方标签快速增删</p>
              </div>

              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label htmlFor="city">工作城市</Label>
                  <button
                    type="button"
                    onClick={() => {
                      setIsCustomCity(!isCustomCity)
                      if (!isCustomCity) {
                        // 切换到手动输入时，清空当前值
                        setConfig({ ...config, city: '' })
                      }
                    }}
                    className="text-xs text-primary hover:underline"
                  >
                    {isCustomCity ? '从列表选择' : '手动输入'}
                  </button>
                </div>
                {isCustomCity ? (
                  <Input
                    id="city"
                    value={config.city || ''}
                    onChange={(e) => setConfig({ ...config, city: e.target.value })}
                    placeholder="请输入城市码，例如：410"
                  />
                ) : (
                  <Select
                    id="city"
                    value={config.city || ''}
                    onChange={(e) => setConfig({ ...config, city: e.target.value })}
                  >
                    <option value="">请选择城市</option>
                    {options.city.map((city) => (
                      <option key={city.id} value={city.name}>
                        {city.name}
                      </option>
                    ))}
                  </Select>
                )}
                <p className="text-xs text-muted-foreground">
                  {isCustomCity ? '手动输入城市码（例如：410代表北京）' : '从列表选择城市，或点击"手动输入"自定义'}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 薪资配置 */}
        <Card className="animate-in fade-in slide-in-from-bottom-6 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiMoney className="text-primary" />
              薪资筛选
            </CardTitle>
            <CardDescription>设置期望薪资范围</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 gap-6">
              <div className="space-y-2">
                <Label htmlFor="salaryCode">薪资范围</Label>
                <Input
                  id="salaryCode"
                  value={config.salaryCode || ''}
                  onChange={(e) => setConfig({ ...config, salaryCode: e.target.value })}
                  placeholder="例如：15$30"
                />
                <p className="text-xs text-muted-foreground">薪资范围码（例如：15$30表示15k-30k）</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 打招呼语与发简历配置（与Boss同步） */}
        <Card className="animate-in fade-in slide-in-from-bottom-7 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiMessageDetail className="text-primary" />
              打招呼语与自动发简历（与Boss同步）
            </CardTitle>
            <CardDescription>点击“聊一聊”打开沟通窗口后，将自动发送定制打招呼语并点击【发简历】投递猎聘内置简历（不再发送图片）</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-6">
              <div className="flex items-center justify-between p-4 rounded-xl border bg-muted/30">
                <div className="space-y-1">
                  <Label htmlFor="liepin-enable-ai" className="text-base font-medium">启用 AI 定制打招呼语</Label>
                  <p className="text-xs text-muted-foreground">根据岗位名称自动调用 DeepSeek 生成匹配度更高的招呼语，失败时回退下方默认招呼语</p>
                </div>
                <input
                  id="liepin-enable-ai"
                  type="checkbox"
                  checked={enableAI}
                  onChange={(e) => {
                    const checked = e.target.checked
                    setEnableAI(checked)
                    syncSharedBossConfig(checked, sayHi)
                  }}
                  className="h-5 w-5 accent-purple-600 cursor-pointer"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="liepin-say-hi">默认打招呼语（AI 关闭或调用失败时兜底使用）</Label>
                <textarea
                  id="liepin-say-hi"
                  rows={3}
                  value={sayHi}
                  onChange={(e) => setSayHi(e.target.value)}
                  onBlur={() => syncSharedBossConfig(enableAI, sayHi)}
                  placeholder="请输入自定义打招呼语..."
                  className="w-full rounded-xl border border-input bg-background px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                />
              </div>

              <div className="border-t pt-4">
                <button
                  type="button"
                  onClick={() => setShowAiAdvanced(!showAiAdvanced)}
                  className="text-sm font-medium text-purple-600 hover:underline flex items-center gap-1.5"
                >
                  <BiBot />
                  {showAiAdvanced ? '收起 AI 个人介绍与筛选 Prompt 自定义' : '展开自定义 AI 个人介绍与筛选 Prompt（失焦自动保存）'}
                </button>
                {showAiAdvanced && (
                  <div className="mt-4 space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="liepin-ai-introduce">个人介绍（AI 生成招呼语时的简历事实底稿）</Label>
                      <textarea
                        id="liepin-ai-introduce"
                        rows={4}
                        value={aiIntroduce}
                        onChange={(e) => setAiIntroduce(e.target.value)}
                        onBlur={() => saveAiProfile(aiIntroduce, aiPrompt)}
                        className="w-full rounded-xl border border-input bg-background px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="liepin-ai-prompt">AI 筛选与招呼语 Prompt 模板</Label>
                      <textarea
                        id="liepin-ai-prompt"
                        rows={4}
                        value={aiPrompt}
                        onChange={(e) => setAiPrompt(e.target.value)}
                        onBlur={() => saveAiProfile(aiIntroduce, aiPrompt)}
                        className="w-full rounded-xl border border-input bg-background px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      />
                    </div>
                  </div>
                )}
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 黑名单管理（与Boss共享，猎聘投递实时拦截） */}
        <Card className="animate-in fade-in slide-in-from-bottom-7 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiShieldQuarter className="text-primary" />
              黑名单管理（与Boss共享）
            </CardTitle>
            <CardDescription>添加或删除不想投递的公司、职位或HR，猎聘遍历卡片时会自动跳过</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-5">
              <div className="flex flex-col sm:flex-row gap-2">
                <Select
                  value={blacklistType}
                  onChange={(e) => setBlacklistType(e.target.value)}
                  className="sm:w-36"
                >
                  <option value="job">职位关键词</option>
                  <option value="company">公司名称</option>
                  <option value="recruiter">HR称呼</option>
                </Select>
                <Input
                  value={newBlacklistKeyword}
                  onChange={(e) => setNewBlacklistKeyword(e.target.value)}
                  placeholder="输入要屏蔽的关键词，回车或点击添加"
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') handleAddBlacklist()
                  }}
                />
                <Button onClick={handleAddBlacklist} className="whitespace-nowrap">
                  <BiPlus className="mr-1" />
                  添加
                </Button>
              </div>

              <div className="space-y-4">
                {[
                  { type: 'job', label: '岗位黑名单', icon: <BiBriefcase className="text-blue-500" /> },
                  { type: 'company', label: '公司黑名单', icon: <BiBuilding className="text-orange-500" /> },
                  { type: 'recruiter', label: 'HR黑名单', icon: <BiSearch className="text-green-500" /> },
                ].map((group) => {
                  const items = blacklist.filter((item) => item.type === group.type)
                  return (
                    <div key={group.type}>
                      <h4 className="text-sm font-semibold mb-2 flex items-center gap-2">
                        {group.icon}
                        <span>
                          {group.label} ({items.length})
                        </span>
                      </h4>
                      <div className="flex flex-wrap gap-2">
                        {items.length === 0 ? (
                          <span className="text-xs text-muted-foreground">暂无记录</span>
                        ) : (
                          items.map((item) => (
                            <span
                              key={item.id}
                              className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs border bg-muted/40"
                            >
                              {item.value}
                              <button
                                type="button"
                                onClick={() => handleDeleteBlacklist(item.id)}
                                className="text-red-500 hover:text-red-700"
                                title="删除"
                              >
                                <BiTrash />
                              </button>
                            </span>
                          ))
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          </CardContent>
        </Card>
        </TabsContent>

        <TabsContent value="analytics" className="space-y-6 mt-6">
          <AnalysisContent />
        </TabsContent>
      </Tabs>

      {/* 退出确认弹框 */}
      {showLogoutDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border border-gray-200 dark:border-neutral-800 animate-in fade-in zoom-in-95">
            <Card className="border-0">
              <CardHeader className="pb-2">
                <CardTitle className="text-lg flex items-center gap-2">
                  <BiLogOut className="text-red-500" />
                  确认退出登录
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-0">
                <p className="text-sm text-muted-foreground mb-4">退出后将清除Cookie并切换为未登录状态。</p>
                <div className="flex justify-end gap-2">
                  <Button
                    variant="ghost"
                    onClick={() => setShowLogoutDialog(false)}
                    className="rounded-full px-4"
                  >
                    取消
                  </Button>
                  <Button
                    onClick={async () => {
                      await triggerLogout()
                      setShowLogoutDialog(false)
                    }}
                    className="rounded-full bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white px-4"
                  >
                    确认退出
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      {/* 退出登录结果弹框 */}
      {showLogoutResultDialog && logoutResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" role="dialog" aria-modal="true">
          <div className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border border-gray-200 dark:border-neutral-800 animate-in fade-in zoom-in-95">
            <Card className="border-0">
              <CardHeader className="pb-2">
                <CardTitle className="text-lg flex items-center gap-2">
                  <BiLogOut className={logoutResult.success ? 'text-green-500' : 'text-red-500'} />
                  {logoutResult.success ? '退出登录成功' : '退出登录失败'}
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-0">
                <p className="text-sm text-muted-foreground mb-4">{logoutResult.message}</p>
                <div className="flex justify-end gap-2">
                  <Button
                    onClick={() => setShowLogoutResultDialog(false)}
                    className={`rounded-full px-4 ${logoutResult.success ? 'bg-gradient-to-r from-green-500 to-emerald-600 hover:from-green-600 hover:to-emerald-700 text-white' : 'bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white'}`}
                  >
                    知道了
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      {/* 保存结果弹框 */}
      {showSaveDialog && saveResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" role="dialog" aria-modal="true">
          <div className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border border-gray-200 dark:border-neutral-800 animate-in fade-in zoom-in-95">
            <Card className="border-0">
              <CardHeader className="pb-2">
                <CardTitle className="text-lg flex items-center gap-2">
                  <BiSave className={saveResult.success ? 'text-green-500' : 'text-red-500'} />
                  {saveResult.success ? '保存成功' : '保存失败'}
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-0">
                <p className="text-sm text-muted-foreground mb-4">{saveResult.message}</p>
                <div className="flex justify-end gap-2">
                  <Button
                    onClick={() => setShowSaveDialog(false)}
                    className={`rounded-full px-4 ${saveResult.success ? 'bg-gradient-to-r from-green-500 to-emerald-600 hover:from-green-600 hover:to-emerald-700 text-white' : 'bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white'}`}
                  >
                    知道了
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}
    </div>
  )
}
