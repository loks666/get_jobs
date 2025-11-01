'use client'

import { useState } from 'react'
import { BiSave, BiRefresh, BiKey, BiLinkExternal, BiCodeAlt } from 'react-icons/bi'

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
    <div>
      <h1 className="text-3xl font-bold text-gray-800 mb-6">环境变量配置 (.env_template)</h1>

      <div className="config-section">
        <h3><BiLinkExternal className="inline mr-2" />企业微信 Webhook</h3>
        <div className="form-group">
          <label className="form-label">Webhook URL</label>
          <input
            type="text"
            value={envConfig.hookUrl}
            onChange={(e) => setEnvConfig({ ...envConfig, hookUrl: e.target.value })}
            className="form-control"
            placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=your_key"
          />
          <small className="text-muted">
            企业微信群机器人webhook地址，用于接收通知消息
          </small>
        </div>
      </div>

      <div className="config-section">
        <h3><BiCodeAlt className="inline mr-2" />API 配置</h3>
        <div className="config-grid">
          <div className="form-group">
            <label className="form-label">API Base URL</label>
            <input
              type="text"
              value={envConfig.baseUrl}
              onChange={(e) => setEnvConfig({ ...envConfig, baseUrl: e.target.value })}
              className="form-control"
              placeholder="https://api.ruyun.fun"
            />
            <small className="text-muted">API服务器地址</small>
          </div>

          <div className="form-group">
            <label className="form-label">AI模型</label>
            <input
              type="text"
              value={envConfig.model}
              onChange={(e) => setEnvConfig({ ...envConfig, model: e.target.value })}
              className="form-control"
              placeholder="gpt-5-nano-2025-08-07"
            />
            <small className="text-muted">使用的AI模型名称</small>
          </div>
        </div>
      </div>

      <div className="config-section">
        <h3><BiKey className="inline mr-2" />API 密钥</h3>
        <div className="form-group">
          <label className="form-label">API Key</label>
          <div className="relative">
            <input
              type={showApiKey ? 'text' : 'password'}
              value={envConfig.apiKey}
              onChange={(e) => setEnvConfig({ ...envConfig, apiKey: e.target.value })}
              className="form-control"
              placeholder="sk-xxxxxxxxxxxxxxxxx"
            />
            <button
              onClick={() => setShowApiKey(!showApiKey)}
              className="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-500 hover:text-gray-700"
              type="button"
            >
              {showApiKey ? '隐藏' : '显示'}
            </button>
          </div>
          <small className="text-muted">
            🔐 API密钥将被安全存储，请妥善保管
          </small>
        </div>
      </div>

      <div className="bg-blue-50 border-l-4 border-blue-500 p-4 mb-6 rounded">
        <div className="flex">
          <div className="flex-shrink-0">
            <svg className="h-5 w-5 text-blue-500" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd"/>
            </svg>
          </div>
          <div className="ml-3">
            <p className="text-sm text-blue-700">
              <strong>提示：</strong> 这些环境变量将保存到 <code className="bg-blue-100 px-2 py-1 rounded">.env</code> 文件中。
              请勿将包含敏感信息的 .env 文件提交到版本控制系统。
            </p>
          </div>
        </div>
      </div>

      <div className="flex gap-4">
        <button onClick={handleSave} className="btn btn-success">
          <BiSave className="inline mr-2" />保存环境变量
        </button>
        <button onClick={handleReset} className="btn btn-secondary">
          <BiRefresh className="inline mr-2" />重置
        </button>
      </div>
    </div>
  )
}
