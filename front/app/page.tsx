'use client'

import { useState } from 'react'
import { BiSave, BiRefresh, BiServer, BiData, BiFile, BiCheckCircle, BiCog } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export default function Home() {
  const [config, setConfig] = useState({
    appName: 'get_jobs',
    profile: 'dev',
    serverPort: '8888',
    dbUrl: 'jdbc:sqlite:./db/config.db',
    dataDbUrl: 'jdbc:sqlite:./db/data.db',
    logLevel: 'INFO',
    logFile: './target/logs/get-jobs.log',
  })

  const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved'>('idle')

  const handleSave = () => {
    setSaveStatus('saving')
    console.log('Saving config:', config)

    // 模拟保存过程
    setTimeout(() => {
      setSaveStatus('saved')
      setTimeout(() => setSaveStatus('idle'), 2000)
    }, 1000)
  }

  const handleReset = () => {
    if (confirm('确定要重置配置吗？')) {
      window.location.reload()
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50 p-6">
      {/* 页面标题区域 */}
      <div className="mb-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
        <div className="flex items-center gap-4 mb-2">
          <div className="p-3 bg-gradient-to-r from-blue-500 to-indigo-600 rounded-xl text-white shadow-lg">
            <BiCog className="text-2xl" />
          </div>
          <div>
            <h1 className="text-4xl font-bold bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 bg-clip-text text-transparent">
              主配置管理
            </h1>
            <p className="text-muted-foreground mt-1">application.yaml 配置文件管理</p>
          </div>
        </div>
      </div>

      {/* 主配置卡片 */}
      <div className="max-w-6xl mx-auto space-y-6">

        {/* 应用配置 */}
        <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-blue-700">
              <BiServer className="text-blue-500" />
              应用配置
            </CardTitle>
            <CardDescription>配置应用的基本信息和运行参数</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              <div className="space-y-2">
                <Label htmlFor="appName">应用名称</Label>
                <Input
                  id="appName"
                  value={config.appName}
                  onChange={(e) => setConfig({ ...config, appName: e.target.value })}
                  placeholder="输入应用名称"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="profile">环境配置</Label>
                <select
                  id="profile"
                  value={config.profile}
                  onChange={(e) => setConfig({ ...config, profile: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="dev">开发环境 (dev)</option>
                  <option value="prod">生产环境 (prod)</option>
                  <option value="test">测试环境 (test)</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="serverPort">服务器端口</Label>
                <Input
                  id="serverPort"
                  type="number"
                  value={config.serverPort}
                  onChange={(e) => setConfig({ ...config, serverPort: e.target.value })}
                  placeholder="8888"
                />
                <p className="text-xs text-muted-foreground">应用运行的端口号</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 数据库配置 */}
        <Card className="animate-in fade-in slide-in-from-bottom-6 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-indigo-700">
              <BiData className="text-indigo-500" />
              数据库配置
            </CardTitle>
            <CardDescription>配置 SQLite 数据库文件路径</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="dbUrl">配置数据库路径</Label>
                <Input
                  id="dbUrl"
                  value={config.dbUrl}
                  onChange={(e) => setConfig({ ...config, dbUrl: e.target.value })}
                  placeholder="jdbc:sqlite:./db/config.db"
                />
                <p className="text-xs text-muted-foreground">SQLite 配置数据库文件路径</p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="dataDbUrl">数据数据库路径</Label>
                <Input
                  id="dataDbUrl"
                  value={config.dataDbUrl}
                  onChange={(e) => setConfig({ ...config, dataDbUrl: e.target.value })}
                  placeholder="jdbc:sqlite:./db/data.db"
                />
                <p className="text-xs text-muted-foreground">SQLite 数据库文件路径</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 日志配置 */}
        <Card className="animate-in fade-in slide-in-from-bottom-7 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-purple-700">
              <BiFile className="text-purple-500" />
              日志配置
            </CardTitle>
            <CardDescription>配置应用日志级别和文件路径</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="logLevel">日志级别</Label>
                <select
                  id="logLevel"
                  value={config.logLevel}
                  onChange={(e) => setConfig({ ...config, logLevel: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="DEBUG">DEBUG - 调试信息</option>
                  <option value="INFO">INFO - 一般信息</option>
                  <option value="WARN">WARN - 警告信息</option>
                  <option value="ERROR">ERROR - 错误信息</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="logFile">日志文件路径</Label>
                <Input
                  id="logFile"
                  value={config.logFile}
                  onChange={(e) => setConfig({ ...config, logFile: e.target.value })}
                  placeholder="./target/logs/get-jobs.log"
                />
                <p className="text-xs text-muted-foreground">日志文件存储位置</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 操作按钮 */}
        <div className="flex flex-col sm:flex-row gap-4 justify-center items-center animate-in fade-in slide-in-from-bottom-8 duration-700">
          <Button
            onClick={handleSave}
            disabled={saveStatus === 'saving'}
            size="lg"
            className="min-w-[160px]"
          >
            {saveStatus === 'saving' ? (
              <>
                <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent"></div>
                保存中...
              </>
            ) : saveStatus === 'saved' ? (
              <>
                <BiCheckCircle />
                已保存
              </>
            ) : (
              <>
                <BiSave />
                保存配置
              </>
            )}
          </Button>

          <Button onClick={handleReset} variant="outline" size="lg" className="min-w-[160px]">
            <BiRefresh />
            重置配置
          </Button>
        </div>

        {/* 状态卡片 */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-8 animate-in fade-in slide-in-from-bottom-9 duration-700">
          <Card className="text-center hover:shadow-lg transition-shadow">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-blue-600 mb-1">3</div>
              <div className="text-sm text-muted-foreground">配置模块</div>
            </CardContent>
          </Card>

          <Card className="text-center hover:shadow-lg transition-shadow">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-indigo-600 mb-1">7</div>
              <div className="text-sm text-muted-foreground">配置项目</div>
            </CardContent>
          </Card>

          <Card className="text-center hover:shadow-lg transition-shadow">
            <CardContent className="pt-6">
              <div className="flex items-center justify-center gap-2">
                <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                <div className="text-sm font-medium text-foreground">配置就绪</div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
