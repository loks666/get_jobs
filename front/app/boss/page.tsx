'use client'

import { useState, useEffect } from 'react'
import { BiBriefcase, BiSave, BiSearch, BiMap, BiMoney, BiBuilding, BiTime, BiBarChart, BiTrash, BiPlus } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import PageHeader from '@/app/components/PageHeader'

interface BossConfig {
  id?: number
  debugger?: number
  waitTime?: number
  keywords?: string
  cityCode?: string
  industry?: string
  jobType?: string
  experience?: string
  degree?: string
  salary?: string
  scale?: string
  stage?: string
  sayHi?: string
  expectedSalaryMin?: number
  expectedSalaryMax?: number
  enableAi?: number
  sendImgResume?: number
  filterDeadHr?: number
  deadStatus?: string
}

interface BossOption {
  id: number
  type: string
  name: string
  code: string
}

interface BossOptions {
  city: BossOption[]
  industry: BossOption[]
  experience: BossOption[]
  jobType: BossOption[]
  salary: BossOption[]
  degree: BossOption[]
  scale: BossOption[]
  stage: BossOption[]
}

interface BlacklistItem {
  id: number
  value: string
  type: string
}

export default function BossPage() {
  const [config, setConfig] = useState<BossConfig>({
    keywords: '',
    cityCode: '',
    industry: '',
    jobType: '',
    experience: '',
    degree: '',
    salary: '',
    scale: '',
    stage: '',
    expectedSalaryMin: 0,
    expectedSalaryMax: 0,
  })
  const [options, setOptions] = useState<BossOptions>({
    city: [],
    industry: [],
    experience: [],
    jobType: [],
    salary: [],
    degree: [],
    scale: [],
    stage: [],
  })
  const [blacklist, setBlacklist] = useState<BlacklistItem[]>([])
  const [newBlacklistKeyword, setNewBlacklistKeyword] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchAllData()
  }, [])

  const fetchAllData = async () => {
    try {
      const response = await fetch('http://localhost:8888/api/boss/config')
      const data = await response.json()

      if (data.config) {
        setConfig(data.config)
      }
      if (data.options) {
        setOptions(data.options)
      }
      if (data.blacklist) {
        setBlacklist(data.blacklist)
      }
    } catch (error) {
      console.error('Failed to fetch data:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async () => {
    try {
      const response = await fetch('http://localhost:8888/api/boss/config', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(config),
      })

      if (response.ok) {
        alert('Boss直聘配置已保存！')
        fetchAllData()
      } else {
        alert('保存失败，请重试')
      }
    } catch (error) {
      console.error('Failed to save config:', error)
      alert('保存失败，请重试')
    }
  }

  const handleAddBlacklist = async () => {
    if (!newBlacklistKeyword.trim()) {
      alert('请输入黑名单关键词')
      return
    }

    try {
      const response = await fetch('http://localhost:8888/api/boss/config/blacklist', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          value: newBlacklistKeyword,
          type: 'boss',
        }),
      })

      if (response.ok) {
        setNewBlacklistKeyword('')
        fetchAllData()
      } else {
        alert('添加失败，请重试')
      }
    } catch (error) {
      console.error('Failed to add blacklist:', error)
      alert('添加失败，请重试')
    }
  }

  const handleDeleteBlacklist = async (id: number) => {
    try {
      const response = await fetch(`http://localhost:8888/api/boss/config/blacklist/${id}`, {
        method: 'DELETE',
      })

      if (response.ok) {
        fetchAllData()
      } else {
        alert('删除失败，请重试')
      }
    } catch (error) {
      console.error('Failed to delete blacklist:', error)
      alert('删除失败，请重试')
    }
  }

  if (loading) {
    return <div className="flex items-center justify-center h-screen">加载中...</div>
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiBriefcase className="text-2xl" />}
        title="Boss直聘配置"
        subtitle="配置Boss直聘平台的求职参数"
        iconClass="text-white"
        accentBgClass="bg-teal-500"
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
              <CardDescription>设置职位搜索关键词和目标城市</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
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
                  <select
                    id="city"
                    value={config.cityCode || ''}
                    onChange={(e) => setConfig({ ...config, cityCode: e.target.value })}
                    className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                  >
                    <option value="">请选择</option>
                    {options.city.map((city) => (
                      <option key={city.id} value={city.code}>
                        {city.name}
                      </option>
                    ))}
                  </select>
                  <p className="text-xs text-muted-foreground">目标工作城市</p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="industry">行业类型</Label>
                  <select
                    id="industry"
                    value={config.industry || ''}
                    onChange={(e) => setConfig({ ...config, industry: e.target.value })}
                    className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                  >
                    <option value="">请选择</option>
                    {options.industry.map((industry) => (
                      <option key={industry.id} value={industry.code}>
                        {industry.name}
                      </option>
                    ))}
                  </select>
                  <p className="text-xs text-muted-foreground">目标行业类型</p>
                </div>
              </div>
            </CardContent>
        </Card>

        {/* 薪资和经验 */}
        <Card className="animate-in fade-in slide-in-from-bottom-6 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiMoney className="text-primary" />
              薪资与经验要求
            </CardTitle>
            <CardDescription>设置期望薪资范围和工作经验要求</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="space-y-2">
                <Label htmlFor="salaryMin">期望薪资最低 (K/月)</Label>
                <Input
                  id="salaryMin"
                  type="number"
                  value={config.expectedSalaryMin || ''}
                  onChange={(e) => setConfig({ ...config, expectedSalaryMin: parseInt(e.target.value) || 0 })}
                  placeholder="15"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="salaryMax">期望薪资最高 (K/月)</Label>
                <Input
                  id="salaryMax"
                  type="number"
                  value={config.expectedSalaryMax || ''}
                  onChange={(e) => setConfig({ ...config, expectedSalaryMax: parseInt(e.target.value) || 0 })}
                  placeholder="30"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="experience">工作经验</Label>
                <select
                  id="experience"
                  value={config.experience || ''}
                  onChange={(e) => setConfig({ ...config, experience: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  {options.experience.map((exp) => (
                    <option key={exp.id} value={exp.code}>
                      {exp.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 公司要求 */}
        <Card className="animate-in fade-in slide-in-from-bottom-7 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiBuilding className="text-primary" />
              公司要求
            </CardTitle>
            <CardDescription>设置目标公司的规模和融资阶段</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="space-y-2">
                <Label htmlFor="education">学历要求</Label>
                <select
                  id="education"
                  value={config.degree || ''}
                  onChange={(e) => setConfig({ ...config, degree: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  {options.degree.map((deg) => (
                    <option key={deg.id} value={deg.code}>
                      {deg.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="companySize">公司规模</Label>
                <select
                  id="companySize"
                  value={config.scale || ''}
                  onChange={(e) => setConfig({ ...config, scale: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  {options.scale.map((sc) => (
                    <option key={sc.id} value={sc.code}>
                      {sc.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="financing">融资阶段</Label>
                <select
                  id="financing"
                  value={config.stage || ''}
                  onChange={(e) => setConfig({ ...config, stage: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  {options.stage.map((st) => (
                    <option key={st.id} value={st.code}>
                      {st.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 黑名单管理 */}
        <Card className="animate-in fade-in slide-in-from-bottom-8 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiSearch className="text-primary" />
              黑名单管理
            </CardTitle>
            <CardDescription>添加或删除不想投递的公司或职位关键词</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {/* 添加黑名单 */}
              <div className="flex gap-2">
                <Input
                  value={newBlacklistKeyword}
                  onChange={(e) => setNewBlacklistKeyword(e.target.value)}
                  placeholder="输入黑名单关键词（公司名或职位关键词）"
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      handleAddBlacklist()
                    }
                  }}
                />
                <Button onClick={handleAddBlacklist} className="whitespace-nowrap">
                  <BiPlus />
                  添加
                </Button>
              </div>

              {/* 黑名单列表 */}
              <div className="space-y-2">
                {blacklist.length === 0 ? (
                  <div className="text-center py-8 text-muted-foreground">
                    <p className="text-sm">暂无黑名单</p>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {blacklist.map((item) => (
                      <div
                        key={item.id}
                        className="flex items-center justify-between p-3 bg-muted/50 rounded-lg"
                      >
                        <span className="text-sm">{item.value}</span>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDeleteBlacklist(item.id)}
                          className="text-red-500 hover:text-red-700 hover:bg-red-50"
                        >
                          <BiTrash />
                        </Button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 操作按钮 */}
        <div className="flex justify-center items-center animate-in fade-in slide-in-from-bottom-9 duration-700">
          <Button onClick={handleSave} size="lg" className="min-w-[160px]">
            <BiSave />
            保存配置
          </Button>
        </div>
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
                <BiBriefcase className="text-primary" />
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
                          <BiSearch className="text-4xl mx-auto mb-2 opacity-30" />
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

      {/* 统计卡片已移除 */}
    </div>
  )
}
