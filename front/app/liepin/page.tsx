'use client'

import { useState } from 'react'
import { BiSearch, BiSave, BiTargetLock, BiMap, BiMoney, BiTime, BiBookmark } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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

      <div className="space-y-6">
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

        {/* 统计卡片已移除 */}
      </div>
    </div>
  )
}
