'use client'

import { useState } from 'react'
import { BiUserCircle, BiSave, BiSearchAlt, BiLocationPlus, BiMoney, BiBookAlt, BiLike, BiBarChart, BiTime } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import PageHeader from '@/app/components/PageHeader'

export default function ZhilianPage() {
  const [config, setConfig] = useState({
    keyword: '产品经理',
    city: '杭州',
    salaryMin: '18',
    salaryMax: '35',
    workExperience: '3-5年',
    degree: '本科',
    companyScale: '不限',
    jobType: '全职',
  })

  const handleSave = () => {
    console.log('Saving Zhilian config:', config)
    alert('智联招聘配置已保存！')
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiUserCircle className="text-2xl" />}
        title="智联招聘配置"
        subtitle="配置智联招聘平台的求职参数"
        iconClass="text-white"
        accentBgClass="bg-sky-600"
      />

      <Tabs defaultValue="config" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="config">平台配置</TabsTrigger>
          <TabsTrigger value="analytics">投递分析</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="space-y-6 mt-6">
        {/* 基本搜索 */}
        <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiSearchAlt className="text-primary" />
              基本搜索
            </CardTitle>
            <CardDescription>设置职位关键词和工作城市</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="keyword">职位关键词</Label>
                <Input
                  id="keyword"
                  value={config.keyword}
                  onChange={(e) => setConfig({ ...config, keyword: e.target.value })}
                  placeholder="例如：产品经理"
                />
                <p className="text-xs text-muted-foreground">搜索职位的关键词</p>
              </div>

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
                  <option value="西安">西安</option>
                  <option value="成都">成都</option>
                </select>
                <p className="text-xs text-muted-foreground">期望的工作城市</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 薪资要求 */}
        <Card className="animate-in fade-in slide-in-from-bottom-6 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-green-700">
              <BiMoney className="text-green-500" />
              薪资要求
            </CardTitle>
            <CardDescription>设置期望的薪资范围</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="salaryMin">最低月薪 (K)</Label>
                <Input
                  id="salaryMin"
                  type="number"
                  value={config.salaryMin}
                  onChange={(e) => setConfig({ ...config, salaryMin: e.target.value })}
                  placeholder="18"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="salaryMax">最高月薪 (K)</Label>
                <Input
                  id="salaryMax"
                  type="number"
                  value={config.salaryMax}
                  onChange={(e) => setConfig({ ...config, salaryMax: e.target.value })}
                  placeholder="35"
                />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 工作要求 */}
        <Card className="animate-in fade-in slide-in-from-bottom-7 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lime-700">
              <BiBookAlt className="text-lime-500" />
              工作要求
            </CardTitle>
            <CardDescription>设置工作经验、学历和公司规模</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
              <div className="space-y-2">
                <Label htmlFor="workExperience">工作经验</Label>
                <select
                  id="workExperience"
                  value={config.workExperience}
                  onChange={(e) => setConfig({ ...config, workExperience: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="不限">不限</option>
                  <option value="应届毕业生">应届毕业生</option>
                  <option value="1年以���">1年以下</option>
                  <option value="1-3年">1-3年</option>
                  <option value="3-5年">3-5年</option>
                  <option value="5-10年">5-10年</option>
                  <option value="10年以上">10年以上</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="degree">学历要求</Label>
                <select
                  id="degree"
                  value={config.degree}
                  onChange={(e) => setConfig({ ...config, degree: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="不限">不限</option>
                  <option value="初中及以下">初中及以下</option>
                  <option value="高中">高中</option>
                  <option value="大专">大专</option>
                  <option value="本科">本科</option>
                  <option value="硕士">硕士</option>
                  <option value="博士">博士</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="companyScale">公司规模</Label>
                <select
                  id="companyScale"
                  value={config.companyScale}
                  onChange={(e) => setConfig({ ...config, companyScale: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="不限">不限</option>
                  <option value="少于50人">少于50人</option>
                  <option value="50-150人">50-150人</option>
                  <option value="150-500人">150-500人</option>
                  <option value="500-1000人">500-1000人</option>
                  <option value="1000-5000人">1000-5000人</option>
                  <option value="5000人以上">5000人以上</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="jobType">工作类型</Label>
                <select
                  id="jobType"
                  value={config.jobType}
                  onChange={(e) => setConfig({ ...config, jobType: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="全职">全职</option>
                  <option value="兼职">兼职</option>
                  <option value="实习">实习</option>
                  <option value="劳务派遣">劳务派遣</option>
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
                <BiUserCircle className="text-primary" />
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
                          <BiSearchAlt className="text-4xl mx-auto mb-2 opacity-30" />
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
