'use client'

import { useState, useEffect } from 'react'
import { BiSearch, BiSave, BiTargetLock, BiMap, BiMoney, BiTime, BiBookmark, BiBarChart } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import PageHeader from '@/app/components/PageHeader'

interface LiepinConfig {
  id?: number
  keywords?: string
  city?: string
  salary?: string
  publishTime?: string
}

interface LiepinOption {
  id: number
  type: string
  name: string
  code: string
}

interface LiepinOptions {
  city: LiepinOption[]
  publishTime: LiepinOption[]
}

export default function LiepinPage() {
  const [config, setConfig] = useState<LiepinConfig>({
    keywords: '',
    city: '',
    salary: '',
    publishTime: '',
  })
  const [options, setOptions] = useState<LiepinOptions>({
    city: [],
    publishTime: [],
  })
  const [loading, setLoading] = useState(true)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)

  useEffect(() => {
    fetchAllData()
  }, [])

  const fetchAllData = async () => {
    try {
      const response = await fetch('http://localhost:8888/api/liepin/config')
      const data = await response.json()

      console.log('Fetched liepin data:', data)

      if (data.config) {
        setConfig(data.config)
      }
      if (data.options) {
        setOptions(data.options)
      }
    } catch (error) {
      console.error('Failed to fetch liepin data:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async () => {
    try {
      const response = await fetch('http://localhost:8888/api/liepin/config', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(config),
      })

      if (response.ok) {
        fetchAllData()
        setSaveResult({ success: true, message: '保存成功，配置已更新。' })
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
          <Button onClick={handleSave} size="sm" className="rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-600 hover:to-indigo-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
            <BiSave className="mr-1" /> 保存配置
          </Button>
        }
      />

      <Tabs defaultValue="config" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="config">平台配置</TabsTrigger>
          <TabsTrigger value="analytics">投递分析</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="space-y-6 mt-6">
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
                  placeholder="例如：Java开发工程师"
                />
                <p className="text-xs text-muted-foreground">职位搜索的关键词</p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="city">工作城市</Label>
                <Select
                  id="city"
                  value={config.city || ''}
                  onChange={(e) => setConfig({ ...config, city: e.target.value })}
                >
                  {options.city.map((city) => (
                    <option key={city.id} value={city.code}>
                      {city.name}
                    </option>
                  ))}
                </Select>
                <p className="text-xs text-muted-foreground">目标工作城市</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 薪资和发布时间 */}
        <Card className="animate-in fade-in slide-in-from-bottom-6 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiMoney className="text-primary" />
              薪资与时间筛选
            </CardTitle>
            <CardDescription>设置薪资范围和职位发布时间</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="salary">薪资范围</Label>
                <Input
                  id="salary"
                  value={config.salary || ''}
                  onChange={(e) => setConfig({ ...config, salary: e.target.value })}
                  placeholder="例如：20-40K"
                />
                <p className="text-xs text-muted-foreground">期望薪资范围</p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="publishTime">发布时间</Label>
                <Input
                  id="publishTime"
                  value={config.publishTime || ''}
                  onChange={(e) => setConfig({ ...config, publishTime: e.target.value })}
                  placeholder="例如：近三天"
                />
                <p className="text-xs text-muted-foreground">职位发布时间筛选</p>
              </div>
            </div>
          </CardContent>
        </Card>
        </TabsContent>

        <TabsContent value="analytics" className="space-y-6 mt-6">
          {/* 投递数据统计 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card className="border-blue-200 dark:border-blue-800">
              <CardContent className="pt-6">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-muted-foreground mb-1">今日投递</p>
                    <p className="text-3xl font-bold text-blue-600">0</p>
                    <p className="text-xs text-muted-foreground mt-1">今天新增投递数</p>
                  </div>
                  <div className="h-12 w-12 rounded-full bg-blue-100 dark:bg-blue-900 flex items-center justify-center">
                    <BiTime className="text-2xl text-blue-600" />
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card className="border-green-200 dark:border-green-800">
              <CardContent className="pt-6">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-muted-foreground mb-1">累计投递</p>
                    <p className="text-3xl font-bold text-green-600">0</p>
                    <p className="text-xs text-muted-foreground mt-1">总投递岗位数量</p>
                  </div>
                  <div className="h-12 w-12 rounded-full bg-green-100 dark:bg-green-900 flex items-center justify-center">
                    <BiBarChart className="text-2xl text-green-600" />
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* 图表分析 */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* 投递趋势 - 柱状图 */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <BiTime className="text-primary" />
                  投递趋势
                </CardTitle>
                <CardDescription>最近7天投递数量变化</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="h-64 flex items-center justify-center border-2 border-dashed border-gray-300 dark:border-gray-700 rounded-lg">
                  <div className="text-center text-muted-foreground">
                    <BiBarChart className="text-5xl mx-auto mb-2 opacity-30" />
                    <p className="text-sm">柱状图</p>
                    <p className="text-xs mt-1">显示每日投递趋势</p>
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* 薪资分布 - 折线图 */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <BiMoney className="text-primary" />
                  薪资分布
                </CardTitle>
                <CardDescription>不同薪资范围的岗位数量</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="h-64 flex items-center justify-center border-2 border-dashed border-gray-300 dark:border-gray-700 rounded-lg">
                  <div className="text-center text-muted-foreground">
                    <BiBarChart className="text-5xl mx-auto mb-2 opacity-30" />
                    <p className="text-sm">折线图</p>
                    <p className="text-xs mt-1">显示薪资区间分布</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* 岗位数据列表 */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <BiSearch className="text-primary" />
                岗位数据
              </CardTitle>
              <CardDescription>投递的岗位详细信息</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="rounded-lg border">
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead className="bg-muted/50">
                      <tr className="border-b">
                        <th className="px-4 py-3 text-left text-sm font-medium">公司名称</th>
                        <th className="px-4 py-3 text-left text-sm font-medium">岗位名称</th>
                        <th className="px-4 py-3 text-left text-sm font-medium">薪资</th>
                        <th className="px-4 py-3 text-left text-sm font-medium">岗位要求</th>
                        <th className="px-4 py-3 text-left text-sm font-medium">岗位链接</th>
                        <th className="px-4 py-3 text-left text-sm font-medium">状态</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td colSpan={6} className="px-4 py-8 text-center text-muted-foreground">
                          <BiTargetLock className="text-4xl mx-auto mb-2 opacity-30" />
                          <p className="text-sm">暂无岗位数据</p>
                          <p className="text-xs mt-1">开始投递后将显示岗位列表</p>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

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
