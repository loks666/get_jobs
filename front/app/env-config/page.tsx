'use client'

import { useState, useEffect } from 'react'
import { BiSave, BiKey, BiLinkExternal, BiCodeAlt, BiInfoCircle } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import PageHeader from '@/app/components/PageHeader'
import { API_BASE } from '@/lib/api'
import ChatGptConnection from './ChatGptConnection'

export default function EnvConfig() {
  const [envConfig, setEnvConfig] = useState({
    hookUrl: '',
    baseUrl: '',
    apiKey: '',
    model: '',
    botIsSend: 0,
    feishuHookUrl: '',
    feishuSecret: '',
    feishuIsSend: false,
    authMode: 'api_key',
    chatGptModel: '',
  })

  const [showApiKey, setShowApiKey] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)

  // 从数据库加载配置
  const fetchConfig = async () => {
    try {
      setLoading(true)
      const response = await fetch(`${API_BASE}/api/config`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      })

      if (!response.ok) {
        throw new Error('获取配置失败')
      }

      const result = await response.json()

      if (result.success && result.data) {
        setEnvConfig({
          hookUrl: result.data.HOOK_URL || '',
          feishuHookUrl: result.data.FEISHU_HOOK_URL || '',
          feishuSecret: result.data.FEISHU_SECRET || '',
          feishuIsSend: ['1', 'true'].includes(String(result.data.FEISHU_BOT_IS_SEND || '').trim().toLowerCase()),
          authMode: result.data.AI_AUTH_MODE || 'api_key',
          chatGptModel: result.data.CHATGPT_MODEL || '',
          baseUrl: result.data.BASE_URL || '',
          apiKey: result.data.API_KEY || '',
          model: result.data.MODEL || '',
          botIsSend: (() => {
            const raw = result.data.BOT_IS_SEND
            const val = String(raw ?? '').trim().toLowerCase()
            return val === '1' || val === 'true' ? 1 : 0
          })(),
        })
      }
    } catch (error) {
      console.error('获取配置失败:', error)
      alert('获取配置失败，请检查后端服务是否正常运行')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchConfig()
  }, [])

  const handleSave = async (silent: boolean = false) => {
    try {
      setSaving(true)

      const configMap = {
        HOOK_URL: envConfig.hookUrl,
        FEISHU_HOOK_URL: envConfig.feishuHookUrl.trim(),
        FEISHU_SECRET: envConfig.feishuSecret.trim(),
        FEISHU_BOT_IS_SEND: String(envConfig.feishuIsSend),
        AI_AUTH_MODE: envConfig.authMode,
        CHATGPT_MODEL: envConfig.chatGptModel,
        BASE_URL: envConfig.baseUrl,
        API_KEY: envConfig.apiKey,
        MODEL: envConfig.model,
        BOT_IS_SEND: String(envConfig.botIsSend ?? 0),
      }

      const response = await fetch(`${API_BASE}/api/config`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(configMap),
      })

      if (!response.ok) {
        throw new Error('保存配置失败')
      }

      const result = await response.json()

      if (result.success) {
        if (!silent) {
          setSaveResult({ success: true, message: '保存成功' })
          setShowSaveDialog(true)
        }
      } else {
        throw new Error(result.message || '保存配置失败')
      }
    } catch (error) {
      console.error('保存配置失败:', error)
      if (!silent) {
        setSaveResult({ success: false, message: '保存配置失败：网络或服务异常。' })
        setShowSaveDialog(true)
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiCodeAlt className="text-2xl" />}
        title="环境配置"
        subtitle="通知与模型连接配置"
        actions={
          <Button
            disabled={loading || saving}
            onClick={() => handleSave(false)}
            size="sm"
            className="rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-600 hover:to-indigo-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105"
          >
            <BiSave className="mr-1" /> 保存配置
          </Button>
        }
      />

      {loading && (
        <Card className="border-blue-500/20 bg-blue-500/5">
          <CardContent className="pt-6">
            <p className="text-center text-sm text-muted-foreground">加载配置中...</p>
          </CardContent>
        </Card>
      )}

      <div className="space-y-6">
        {/* 企业微信 Webhook */}
        <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
          <CardHeader className="flex items-start gap-4">
            <div className="min-w-0 space-y-2">
              <CardTitle className="flex items-center gap-2">
                <BiLinkExternal className="text-primary" />
                企业微信 Webhook
              </CardTitle>
              <CardDescription>配置企业微信群机器人，用于接收通知消息</CardDescription>
            </div>
            <div>
              <button
                type="button"
                aria-label="企业微信发送开关"
                onClick={() => setEnvConfig({ ...envConfig, botIsSend: envConfig.botIsSend ? 0 : 1 })}
                className={`relative inline-flex h-7 w-14 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-emerald-400/40 border border-white/30 shadow-[inset_0_1px_0_rgba(255,255,255,.25)] ${envConfig.botIsSend ? 'bg-emerald-500/80 hover:bg-emerald-500' : 'bg-white/10 hover:bg-white/15'}`}
              >
                <span
                  className={`absolute top-1 left-1 h-5 w-5 rounded-full bg-white shadow transition-transform ${envConfig.botIsSend ? 'translate-x-7' : 'translate-x-0'}`}
                />
              </button>
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              <Label htmlFor="hookUrl">Webhook URL</Label>
              <Input
                id="hookUrl"
                type="text"
                value={envConfig.hookUrl}
                onChange={(e) => setEnvConfig({ ...envConfig, hookUrl: e.target.value })}
                placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=your_key"
              />
              <p className="text-xs text-muted-foreground">
                企业微信群机器人webhook地址，用于接收通知消息
              </p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><BiLinkExternal className="text-primary" />飞书 Webhook</CardTitle>
            <CardDescription>发送运行通知到飞书群，可与企业微信同时开启。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={envConfig.feishuIsSend}
                onChange={e => setEnvConfig({ ...envConfig, feishuIsSend: e.target.checked })} />
              启用飞书通知
            </label>
            <div className="space-y-2">
              <Label htmlFor="feishuHookUrl">飞书 Webhook URL</Label>
              <Input id="feishuHookUrl" type="password" value={envConfig.feishuHookUrl} autoComplete="off"
                onChange={e => setEnvConfig({ ...envConfig, feishuHookUrl: e.target.value })}
                placeholder="https://open.feishu.cn/open-apis/bot/v2/hook/…" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="feishuSecret">签名密钥（可选）</Label>
              <Input id="feishuSecret" type="password" value={envConfig.feishuSecret} autoComplete="off"
                onChange={e => setEnvConfig({ ...envConfig, feishuSecret: e.target.value })}
                placeholder="机器人开启签名校验时填写" />
              <p className="text-xs text-muted-foreground">在飞书群添加自定义机器人，复制 Webhook 地址；开启签名校验时一并填写密钥。</p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>模型登录方式</CardTitle>
            <CardDescription>选择 ChatGPT 账号登录或兼容 OpenAI 的 API Key。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <Label htmlFor="authMode">登录方式</Label>
            <select id="authMode" value={envConfig.authMode}
              onChange={e => setEnvConfig({ ...envConfig, authMode: e.target.value })}
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm">
              <option value="api_key">API Key</option>
              <option value="chatgpt">ChatGPT 登录</option>
            </select>
            {envConfig.authMode === 'chatgpt' && <ChatGptConnection model={envConfig.chatGptModel}
              onModelChange={chatGptModel => setEnvConfig(previous => ({ ...previous, chatGptModel }))} />}
          </CardContent>
        </Card>

        {/* API 配置 */}
        {envConfig.authMode === 'api_key' && <>
        <Card className="animate-in fade-in slide-in-from-bottom-6 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiCodeAlt className="text-primary" />
              API 配置
            </CardTitle>
            <CardDescription>配置 API 服务器地址和使用的 AI 模型</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="baseUrl">API Base URL</Label>
                <Input
                  id="baseUrl"
                  type="text"
                  value={envConfig.baseUrl}
                  onChange={(e) => setEnvConfig({ ...envConfig, baseUrl: e.target.value })}
                  placeholder="https://api.openai.com"
                />
                <p className="text-xs text-muted-foreground">API服务器地址</p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="model">AI模型</Label>
                <Input
                  id="model"
                  type="text"
                  value={envConfig.model}
                  onChange={(e) => setEnvConfig({ ...envConfig, model: e.target.value })}
                  placeholder="gpt-5-nano-2025-08-07"
                />
                <p className="text-xs text-muted-foreground">使用的AI模型名称</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* API 密钥 */}
        <Card className="animate-in fade-in slide-in-from-bottom-7 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiKey className="text-primary" />
              API 密钥
            </CardTitle>
            <CardDescription>配置 API 访问密钥，请妥善保管</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              <Label htmlFor="apiKey">API Key</Label>
              <div className="relative">
                <Input
                  id="apiKey"
                  type={showApiKey ? 'text' : 'password'}
                  value={envConfig.apiKey}
                  onChange={(e) => setEnvConfig({ ...envConfig, apiKey: e.target.value })}
                  placeholder="sk-xxxxxxxxxxxxxxxxx"
                />
                <Button
                  onClick={() => setShowApiKey(!showApiKey)}
                  variant="ghost"
                  size="sm"
                  className="absolute right-1 top-1/2 -translate-y-1/2 h-7"
                  type="button"
                >
                  {showApiKey ? '隐藏' : '显示'}
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">
                🔐 API密钥将被安全存储，请妥善保管
              </p>
            </div>
          </CardContent>
        </Card>

        </>}

        {/* 安全提示 */}
        <Card className="border-primary/20 bg-primary/5 animate-in fade-in slide-in-from-bottom-8 duration-700">
          <CardContent className="pt-6">
            <div className="flex gap-3">
              <BiInfoCircle className="h-5 w-5 text-primary flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm text-foreground">
                  <strong className="font-semibold">提示：</strong> 这些设置将保存到本机数据库{' '}
                  <code className="bg-primary/10 px-2 py-0.5 rounded text-primary font-mono text-xs">db/getjobs.db</code>{' '}
                  中。ChatGPT 登录凭证由官方 Codex 保存在本应用独立目录 .chatgpt/，请勿分享或提交这些文件。
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 操作按钮已移至页头右上角 */}

        {/* 保存结果弹框 —— 与 Boss 配置一致样式 */}
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
    </div>
  )
}
