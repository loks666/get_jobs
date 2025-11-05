'use client'

import { useState, useEffect } from 'react'
import { BiSave, BiKey, BiLinkExternal, BiCodeAlt, BiInfoCircle } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import PageHeader from '@/app/components/PageHeader'

export default function EnvConfig() {
  const [envConfig, setEnvConfig] = useState({
    hookUrl: '',
    baseUrl: '',
    apiKey: '',
    model: '',
  })

  const [showApiKey, setShowApiKey] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  // 从数据库加载配置
  const fetchConfig = async () => {
    try {
      setLoading(true)
      const response = await fetch('http://localhost:8888/api/config', {
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
          baseUrl: result.data.BASE_URL || '',
          apiKey: result.data.API_KEY || '',
          model: result.data.MODEL || '',
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

  const handleSave = async () => {
    try {
      setSaving(true)

      const configMap = {
        HOOK_URL: envConfig.hookUrl,
        BASE_URL: envConfig.baseUrl,
        API_KEY: envConfig.apiKey,
        MODEL: envConfig.model,
      }

      const response = await fetch('http://localhost:8888/api/config', {
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
        alert(`环境变量配置已保存！共更新 ${result.updateCount} 项`)
      } else {
        throw new Error(result.message || '保存配置失败')
      }
    } catch (error) {
      console.error('保存配置失败:', error)
      alert('保存配置失败: ' + error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiCodeAlt className="text-2xl" />}
        title="环境变量配置"
        subtitle=".env_template 环境变量管理"
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
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiLinkExternal className="text-primary" />
              企业微信 Webhook
            </CardTitle>
            <CardDescription>配置企业微信群机器人，用于接收通知消息</CardDescription>
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

        {/* API 配置 */}
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
                  placeholder="https://api.ruyun.fun"
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

        {/* 安全提示 */}
        <Card className="border-primary/20 bg-primary/5 animate-in fade-in slide-in-from-bottom-8 duration-700">
          <CardContent className="pt-6">
            <div className="flex gap-3">
              <BiInfoCircle className="h-5 w-5 text-primary flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm text-foreground">
                  <strong className="font-semibold">提示：</strong> 这些环境变量将保存到{' '}
                  <code className="bg-primary/10 px-2 py-0.5 rounded text-primary font-mono text-xs">.env</code>{' '}
                  文件中。请勿将包含敏感信息的 .env 文件提交到版本控制系统。
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 操作按钮 */}
        <div className="flex justify-center items-center animate-in fade-in slide-in-from-bottom-9 duration-700">
          <Button onClick={handleSave} size="lg" className="min-w-[180px]" disabled={loading || saving}>
            <BiSave />
            {saving ? '保存中...' : '保存环境变量'}
          </Button>
        </div>
      </div>
    </div>
  )
}
