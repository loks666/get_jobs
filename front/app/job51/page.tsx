'use client'

import { useState } from 'react'
import { BiTask, BiSave, BiRefresh, BiListUl, BiMapAlt, BiDollar, BiCalendar, BiFilter } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

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

  const handleReset = () => {
    if (confirm('确定要重置配置吗？')) {
      window.location.reload()
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-amber-50 via-orange-50 to-yellow-50 p-6">
      {/* 页面标题 */}
      <div className="mb-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
        <div className="flex items-center gap-4 mb-2">
          <div className="p-3 bg-gradient-to-r from-amber-500 to-orange-600 rounded-xl text-white shadow-lg">
            <BiTask className="text-2xl" />
          </div>
          <div>
            <h1 className="text-4xl font-bold bg-gradient-to-r from-amber-600 via-orange-600 to-yellow-600 bg-clip-text text-transparent">
              51job配置
            </h1>
            <p className="text-muted-foreground mt-1">配置前程无忧平台的求职参数</p>
          </div>
        </div>
      </div>

      <div className="max-w-6xl mx-auto space-y-6">
        {/* 职位信息 */}
        <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-amber-700">
              <BiListUl className="text-amber-500" />
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
        <div className="flex flex-col sm:flex-row gap-4 justify-center items-center animate-in fade-in slide-in-from-bottom-8 duration-700">
          <Button onClick={handleSave} size="lg" className="min-w-[160px]">
            <BiSave />
            保存配置
          </Button>
          <Button onClick={handleReset} variant="outline" size="lg" className="min-w-[160px]">
            <BiRefresh />
            重置配置
          </Button>
        </div>

        {/* 统计卡片 */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-8 animate-in fade-in slide-in-from-bottom-9 duration-700">
          <Card className="text-center hover:shadow-lg transition-shadow">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-amber-600 mb-1">234</div>
              <div className="text-sm text-muted-foreground">匹配职位</div>
            </CardContent>
          </Card>

          <Card className="text-center hover:shadow-lg transition-shadow">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-orange-600 mb-1">45</div>
              <div className="text-sm text-muted-foreground">已申请</div>
            </CardContent>
          </Card>

          <Card className="text-center hover:shadow-lg transition-shadow">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-yellow-600 mb-1">12</div>
              <div className="text-sm text-muted-foreground">企业查看</div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
