'use client'

import { useState } from 'react'
import { BiSearch, BiSave, BiTargetLock, BiMap, BiMoney, BiTime, BiBookmark, BiBarChart } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import PageHeader from '@/app/components/PageHeader'

export default function LiepinPage() {
  const [config, setConfig] = useState({
    keywords: '前端开发工程师',
    industry: '互联网/IT',
    city: '上海',
    salaryMin: '20',
    salaryMax: '40',
    jobType: '全职',
    companyType: '不限',
    updateTime: '近三天',
  })

  const handleSave = () => {
    console.log('Saving Liepin config:', config)
    alert('猎聘配置已保存！')
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiSearch className="text-2xl" />}
        title="猎聘配置"
        subtitle="配置猎聘平台的求职参数"
        iconClass="text-white"
        accentBgClass="bg-orange-500"
      />

      <Tabs defaultValue="config" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="config">平台配置</TabsTrigger>
          <TabsTrigger value="analytics">投递分析</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="space-y-6 mt-6">
        {/* 职位搜索 */}
        <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiTargetLock className="text-primary" />
              职位搜索
            </CardTitle>
            <CardDescription>设置目标职位和行业</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="keywords">职位关键词</Label>
                <Input
                  id="keywords"
                  value={config.keywords}
                  onChange={(e) => setConfig({ ...config, keywords: e.target.value })}
                  placeholder="例如：前端开发工程师"
                />
                <p className="text-xs text-muted-foreground">搜索的职位名称或关键词</p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="industry">目标行业</Label>
                <select
                  id="industry"
                  value={config.industry}
                  onChange={(e) => setConfig({ ...config, industry: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="互联网/IT">互联网/IT</option>
                  <option value="金融">金融</option>
                  <option value="教育">教育</option>
                  <option value="医疗健康">医疗健康</option>
                  <option value="电子商务">电子商务</option>
                  <option value="制造业">制造业</option>
                </select>
                <p className="text-xs text-muted-foreground">目标行业领域</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 地区和薪资 */}
        <Card className="animate-in fade-in slide-in-from-bottom-6 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-cyan-700">
              <BiMap className="text-cyan-500" />
              地区与薪资
            </CardTitle>
            <CardDescription>设置工作地点和期望薪资</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="space-y-2">
                <Label htmlFor="city">工作城市</Label>
                <select
                  id="city"
                  value={config.city}
                  onChange={(e) => setConfig({ ...config, city: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="北京">北京</option>
                  <option value="上海">上海</option>
                  <option value="广州">广州</option>
                  <option value="深圳">深圳</option>
                  <option value="杭州">杭州</option>
                  <option value="南京">南京</option>
                  <option value="苏州">苏州</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="salaryMin">最低年薪 (万)</Label>
                <Input
                  id="salaryMin"
                  type="number"
                  value={config.salaryMin}
                  onChange={(e) => setConfig({ ...config, salaryMin: e.target.value })}
                  placeholder="20"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="salaryMax">最高年薪 (万)</Label>
                <Input
                  id="salaryMax"
                  type="number"
                  value={config.salaryMax}
                  onChange={(e) => setConfig({ ...config, salaryMax: e.target.value })}
                  placeholder="40"
                />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 职位类型和筛选 */}
        <Card className="animate-in fade-in slide-in-from-bottom-7 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiBookmark className="text-primary" />
              职位筛选
            </CardTitle>
            <CardDescription>设置职位类型和更新时间</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="space-y-2">
                <Label htmlFor="jobType">职位类型</Label>
                <select
                  id="jobType"
                  value={config.jobType}
                  onChange={(e) => setConfig({ ...config, jobType: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="全职">全职</option>
                  <option value="兼职">兼职</option>
                  <option value="实习">实习</option>
                  <option value="外包">外包</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="companyType">公司类型</Label>
                <select
                  id="companyType"
                  value={config.companyType}
                  onChange={(e) => setConfig({ ...config, companyType: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="不限">不限</option>
                  <option value="上市公司">上市公司</option>
                  <option value="外资企业">外资企业</option>
                  <option value="国企">国企</option>
                  <option value="创业公司">创业公司</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="updateTime">更新时间</Label>
                <select
                  id="updateTime"
                  value={config.updateTime}
                  onChange={(e) => setConfig({ ...config, updateTime: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="不限">不限</option>
                  <option value="今天">今天</option>
                  <option value="近三天">近三天</option>
                  <option value="近一周">近一周</option>
                  <option value="近一月">近一月</option>
                </select>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 操作按钮 */}
        <div className="flex justify-center items-center animate-in fade-in slide-in-from-bottom-8 duration-700">
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

      {/* 统计卡片已移除 */}
    </div>
  )
}
