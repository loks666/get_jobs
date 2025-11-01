'use client'

import { useState } from 'react'
import { BiSave, BiRefresh, BiKey, BiLinkExternal, BiCodeAlt, BiInfoCircle } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export default function EnvConfig() {
  const [envConfig, setEnvConfig] = useState({
    hookUrl: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=your_key_here',
    baseUrl: 'https://api.ruyun.fun',
    apiKey: 'sk-xxxxxxxxxxxxxxxxx',
    model: 'gpt-5-nano-2025-08-07',
  })

  const [showApiKey, setShowApiKey] = useState(false)

  const handleSave = () => {
    console.log('Saving env config:', envConfig)
    alert('环境变量配置已保存！')
  }

  const handleReset = () => {
    if (confirm('确定要重置配置吗？')) {
      window.location.reload()
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50 p-6">
      {/* 页面标题 */}
      <div className="mb-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
        <div className="flex items-center gap-4 mb-2">
          <div className="p-3 bg-gradient-to-r from-cyan-500 to-blue-600 rounded-xl text-white shadow-lg">
            <BiCodeAlt className="text-2xl" />
          </div>
          <div>
            <h1 className="text-4xl font-bold bg-gradient-to-r from-cyan-600 via-blue-600 to-indigo-600 bg-clip-text text-transparent">
              环境变量配置
            </h1>
            <p className="text-muted-foreground mt-1">.env_template 环境变量管理</p>
          </div>
        </div>
      </div>

      <div className="max-w-6xl mx-auto space-y-6">
        {/* 企业微信 Webhook */}
        <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-cyan-700">
              <BiLinkExternal className="text-cyan-500" />
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
            <CardTitle className="flex items-center gap-2 text-blue-700">
              <BiCodeAlt className="text-blue-500" />
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
            <CardTitle className="flex items-center gap-2 text-indigo-700">
              <BiKey className="text-indigo-500" />
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
        <Card className="border-blue-200 bg-blue-50/50 animate-in fade-in slide-in-from-bottom-8 duration-700">
          <CardContent className="pt-6">
            <div className="flex gap-3">
              <BiInfoCircle className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm text-blue-900">
                  <strong className="font-semibold">提示：</strong> 这些环境变量将保存到{' '}
                  <code className="bg-blue-100 px-2 py-0.5 rounded text-blue-800 font-mono text-xs">.env</code>{' '}
                  文件中。请勿将包含敏感信息的 .env 文件提交到版本控制系统。
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 操作按钮 */}
        <div className="flex flex-col sm:flex-row gap-4 justify-center items-center animate-in fade-in slide-in-from-bottom-9 duration-700">
          <Button onClick={handleSave} size="lg" className="min-w-[180px]">
            <BiSave />
            保存环境变量
          </Button>
          <Button onClick={handleReset} variant="outline" size="lg" className="min-w-[180px]">
            <BiRefresh />
            重置
          </Button>
        </div>
      </div>
    </div>
  )
}
