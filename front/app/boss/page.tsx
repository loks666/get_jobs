'use client'

import { useState } from 'react'
import { BiBriefcase, BiSave, BiSearch, BiMap, BiMoney, BiBuilding, BiTime } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import PageHeader from '@/app/components/PageHeader'

export default function BossPage() {
  const [config, setConfig] = useState({
    keywords: 'Java开发工程师',
    city: '北京',
    salaryMin: '15',
    salaryMax: '30',
    experience: '3-5年',
    education: '本科',
    companySize: '不限',
    financing: '不限',
    autoApply: false,
  })

  const handleSave = () => {
    console.log('Saving Boss config:', config)
    alert('Boss直聘配置已保存！')
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

      <div className="space-y-6">
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
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="keywords">搜索关键词</Label>
                <Input
                  id="keywords"
                  value={config.keywords}
                  onChange={(e) => setConfig({ ...config, keywords: e.target.value })}
                  placeholder="例如：Java开发工程师"
                />
                <p className="text-xs text-muted-foreground">职位搜索的关键词</p>
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
                  <option value="成都">成都</option>
                </select>
                <p className="text-xs text-muted-foreground">目标工作城市</p>
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
                <Label htmlFor="salaryMin">最低薪资 (K/月)</Label>
                <Input
                  id="salaryMin"
                  type="number"
                  value={config.salaryMin}
                  onChange={(e) => setConfig({ ...config, salaryMin: e.target.value })}
                  placeholder="15"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="salaryMax">最高薪资 (K/月)</Label>
                <Input
                  id="salaryMax"
                  type="number"
                  value={config.salaryMax}
                  onChange={(e) => setConfig({ ...config, salaryMax: e.target.value })}
                  placeholder="30"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="experience">工作经验</Label>
                <select
                  id="experience"
                  value={config.experience}
                  onChange={(e) => setConfig({ ...config, experience: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="不限">不限</option>
                  <option value="应届生">应届生</option>
                  <option value="1年以内">1年以内</option>
                  <option value="1-3年">1-3年</option>
                  <option value="3-5年">3-5年</option>
                  <option value="5-10年">5-10年</option>
                  <option value="10年以上">10年以上</option>
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
                  value={config.education}
                  onChange={(e) => setConfig({ ...config, education: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="不限">不限</option>
                  <option value="大专">大专</option>
                  <option value="本科">本科</option>
                  <option value="硕士">硕士</option>
                  <option value="博士">博士</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="companySize">公司规模</Label>
                <select
                  id="companySize"
                  value={config.companySize}
                  onChange={(e) => setConfig({ ...config, companySize: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="不限">不限</option>
                  <option value="0-20人">0-20人</option>
                  <option value="20-99人">20-99人</option>
                  <option value="100-499人">100-499人</option>
                  <option value="500-999人">500-999人</option>
                  <option value="1000人以上">1000人以上</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="financing">融资阶段</Label>
                <select
                  id="financing"
                  value={config.financing}
                  onChange={(e) => setConfig({ ...config, financing: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm"
                >
                  <option value="不限">不限</option>
                  <option value="未融资">未融资</option>
                  <option value="天使轮">天使轮</option>
                  <option value="A轮">A轮</option>
                  <option value="B轮">B轮</option>
                  <option value="C轮及以上">C轮及以上</option>
                  <option value="已上市">已上市</option>
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
