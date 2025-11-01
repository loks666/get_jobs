'use client'

import { useState } from 'react'
import { BiUserCircle, BiSave, BiRefresh, BiSearchAlt, BiLocationPlus, BiMoney, BiBookAlt, BiLike } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

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

  const handleReset = () => {
    if (confirm('确定要重置配置吗？')) {
      window.location.reload()
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-emerald-50 via-green-50 to-lime-50 p-6">
      {/* 页面��题 */}
      <div className="mb-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
        <div className="flex items-center gap-4 mb-2">
          <div className="p-3 bg-gradient-to-r from-emerald-500 to-green-600 rounded-xl text-white shadow-lg">
            <BiUserCircle className="text-2xl" />
          </div>
          <div>
            <h1 className="text-4xl font-bold bg-gradient-to-r from-emerald-600 via-green-600 to-lime-600 bg-clip-text text-transparent">
              智联招聘配置
            </h1>
            <p className="text-muted-foreground mt-1">配置智联招聘平台的求职参数</p>
          </div>
        </div>
      </div>

      <div className="max-w-6xl mx-auto space-y-6">
        {/* 基本搜索 */}
        <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-emerald-700">
              <BiSearchAlt className="text-emerald-500" />
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
              <div className="text-3xl font-bold text-emerald-600 mb-1">178</div>
              <div className="text-sm text-muted-foreground">推荐职位</div>
            </CardContent>
          </Card>

          <Card className="text-center hover:shadow-lg transition-shadow">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-green-600 mb-1">34</div>
              <div className="text-sm text-muted-foreground">投递简历</div>
            </CardContent>
          </Card>

          <Card className="text-center hover:shadow-lg transition-shadow">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-lime-600 mb-1">9</div>
              <div className="text-sm text-muted-foreground">HR查看</div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
