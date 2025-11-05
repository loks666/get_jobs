'use client'

import { useState } from 'react'
import { BiTask, BiSave, BiListUl, BiMapAlt, BiDollar, BiCalendar, BiFilter, BiBarChart, BiTime } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import PageHeader from '@/app/components/PageHeader'

export default function Job51Page() {
  const [config, setConfig] = useState({
    position: 'Python工程师',
    location: '深圳',
    salaryRange: '15-25K',
    workYears: '3-5年',
    education: '本科',
    jobNature: '全职',
    companyNature: '不限',
    industry: '计算机软件',
  })

  const handleSave = () => {
    console.log('Saving 51job config:', config)
    alert('51job配置已保存！')
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiTask className="text-2xl" />}
        title="51job配置"
        subtitle="配置前程无忧平台的求职参数"
        iconClass="text-white"
        accentBgClass="bg-amber-500"
      />

      <Tabs defaultValue="config" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="config">平台配置</TabsTrigger>
          <TabsTrigger value="analytics">投递分析</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="space-y-6 mt-6">
        {/* 职位信息 */}
        <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiListUl className="text-primary" />
              职位信息
            </CardTitle>
            <CardDescription>设置目标职位和工作地点</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="position">职位名称</Label>
                <Input
                  id="position"
                  value={config.position}
                  onChange={(e) => setConfig({ ...config, position: e.target.value })}
                  placeholder="例如：Python工程师"
                />
                <p className="text-xs text-muted-foreground">搜索的职位名称</p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="location">工作地点</Label>
                <select
                  id="location"
                  value={config.location}
                  onChange={(e) => setConfig({ ...config, location: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="北京">北京</option>
                  <option value="上海">上海</option>
                  <option value="广州">广州</option>
                  <option value="深圳">深圳</option>
                  <option value="杭州">杭州</option>
                  <option value="成都">成都</option>
                  <option value="武汉">武汉</option>
                </select>
                <p className="text-xs text-muted-foreground">期望工作城市</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 薪资和经验 */}
        <Card className="animate-in fade-in slide-in-from-bottom-6 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-orange-700">
              <BiDollar className="text-orange-500" />
              薪资与经验
            </CardTitle>
            <CardDescription>设置期望薪资和工作经验</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="space-y-2">
                <Label htmlFor="salaryRange">薪资范围</Label>
                <select
                  id="salaryRange"
                  value={config.salaryRange}
                  onChange={(e) => setConfig({ ...config, salaryRange: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="5-10K">5-10K</option>
                  <option value="10-15K">10-15K</option>
                  <option value="15-25K">15-25K</option>
                  <option value="25-35K">25-35K</option>
                  <option value="35-50K">35-50K</option>
                  <option value="50K以上">50K以上</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="workYears">工作年限</Label>
                <select
                  id="workYears"
                  value={config.workYears}
                  onChange={(e) => setConfig({ ...config, workYears: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="无经验">无经验</option>
                  <option value="1年以下">1年以下</option>
                  <option value="1-3年">1-3年</option>
                  <option value="3-5年">3-5年</option>
                  <option value="5-10年">5-10年</option>
                  <option value="10年以上">10年以上</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="education">学历要求</Label>
                <select
                  id="education"
                  value={config.education}
                  onChange={(e) => setConfig({ ...config, education: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="不限">不限</option>
                  <option value="中专/中技">中专/中技</option>
                  <option value="大专">大专</option>
                  <option value="本科">本科</option>
                  <option value="硕士">硕士</option>
                  <option value="博士">博士</option>
                </select>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 其他筛选 */}
        <Card className="animate-in fade-in slide-in-from-bottom-7 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-yellow-700">
              <BiFilter className="text-yellow-500" />
              高级筛选
            </CardTitle>
            <CardDescription>设置工作性质、公司性质和行业</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="space-y-2">
                <Label htmlFor="jobNature">工作性质</Label>
                <select
                  id="jobNature"
                  value={config.jobNature}
                  onChange={(e) => setConfig({ ...config, jobNature: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="全职">全职</option>
                  <option value="兼职">兼职</option>
                  <option value="实习">实习</option>
                  <option value="派遣">派遣</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="companyNature">公司性质</Label>
                <select
                  id="companyNature"
                  value={config.companyNature}
                  onChange={(e) => setConfig({ ...config, companyNature: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="不限">不限</option>
                  <option value="外资/外企">外资/外企</option>
                  <option value="合资">合资</option>
                  <option value="国企">国企</option>
                  <option value="民营">民营</option>
                  <option value="上市公司">上市公司</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="industry">所属行业</Label>
                <select
                  id="industry"
                  value={config.industry}
                  onChange={(e) => setConfig({ ...config, industry: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="计算机软件">计算机软件</option>
                  <option value="互联网/电子商务">互联网/电子商务</option>
                  <option value="IT服务">IT服务</option>
                  <option value="电子技术">电子技术</option>
                  <option value="通信">通信</option>
                  <option value="金融">金融</option>
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
            {/* 投递趋势 - 折线图 */}
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
                    <p className="text-sm">折线图</p>
                    <p className="text-xs mt-1">显示每日投递趋势</p>
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* 薪资分布 - 柱状图 */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <BiDollar className="text-primary" />
                  薪资分布
                </CardTitle>
                <CardDescription>不同薪资范围的岗位数量</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="h-64 flex items-center justify-center border-2 border-dashed border-gray-300 dark:border-gray-700 rounded-lg">
                  <div className="text-center text-muted-foreground">
                    <BiBarChart className="text-5xl mx-auto mb-2 opacity-30" />
                    <p className="text-sm">柱状图</p>
                    <p className="text-xs mt-1">显示薪资区间分布</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* 投递状态 - 饼状图 */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <BiBarChart className="text-primary" />
                投递状态分布
              </CardTitle>
              <CardDescription>各种投递状态的占比情况</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="h-80 flex items-center justify-center border-2 border-dashed border-gray-300 dark:border-gray-700 rounded-lg">
                <div className="text-center text-muted-foreground">
                  <BiBarChart className="text-5xl mx-auto mb-2 opacity-30" />
                  <p className="text-sm">饼状图</p>
                  <p className="text-xs mt-1">显示已投递、待回复、已拒绝等状态占比</p>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* 投递岗位列表 */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <BiTask className="text-primary" />
                投递岗位数据
              </CardTitle>
              <CardDescription>最近投递的岗位详细信息</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="rounded-lg border">
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead className="bg-muted/50">
                      <tr className="border-b">
                        <th className="px-4 py-3 text-left text-sm font-medium">岗位名称</th>
                        <th className="px-4 py-3 text-left text-sm font-medium">公司名称</th>
                        <th className="px-4 py-3 text-left text-sm font-medium">薪资范围</th>
                        <th className="px-4 py-3 text-left text-sm font-medium">投递时间</th>
                        <th className="px-4 py-3 text-left text-sm font-medium">状态</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
                          <BiListUl className="text-4xl mx-auto mb-2 opacity-30" />
                          <p className="text-sm">暂无投递数据</p>
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
