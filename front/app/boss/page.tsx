'use client'

import { useState, useEffect, useRef, useCallback } from 'react'
import { createSSEWithBackoff } from '@/lib/sse'
import { createPortal } from 'react-dom'
import { BiBriefcase, BiSave, BiSearch, BiMap, BiMoney, BiBuilding, BiTime, BiBarChart, BiTrash, BiPlus, BiPlay, BiStop, BiLogOut } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import PageHeader from '@/app/components/PageHeader'
import AnalysisContent from '@/app/boss/analysis/AnalysisContent'

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
  // 可选的排序字段（来自后端/数据库），用于前端排序显示
  sort_order?: number
  sortOrder?: number
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
    filterDeadHr: 0,
  })
  // 关键词显示用（无括号无引号，逗号分隔）
  const [keywordsDisplay, setKeywordsDisplay] = useState<string>('')
  // 多选选中的代码集合（按括号列表保存）
  const [selectedIndustry, setSelectedIndustry] = useState<string[]>([])
  const [selectedExperience, setSelectedExperience] = useState<string[]>([])
  const [selectedDegree, setSelectedDegree] = useState<string[]>([])
  const [selectedScale, setSelectedScale] = useState<string[]>([])
  const [selectedStage, setSelectedStage] = useState<string[]>([])
  const [selectedSalary, setSelectedSalary] = useState<string[]>([])
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
  const [blacklistType, setBlacklistType] = useState('company') // 默认为公司
  const [loading, setLoading] = useState(true)
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [isDelivering, setIsDelivering] = useState(false)
  const [checkingLogin, setCheckingLogin] = useState(true)
  const [showLogoutDialog, setShowLogoutDialog] = useState(false)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)
  const [showLogoutResultDialog, setShowLogoutResultDialog] = useState(false)
  const [logoutResult, setLogoutResult] = useState<{ success: boolean; message: string } | null>(null)

  useEffect(() => {
    fetchAllData()

    // 确保在客户端环境且 EventSource 可用
    if (typeof window === 'undefined' || typeof EventSource === 'undefined') {
      console.warn('EventSource 不可用，无法连接SSE')
      setCheckingLogin(false)
      return
    }

    const client = createSSEWithBackoff('http://localhost:8888/api/jobs/login-status/stream', {
      onOpen: () => {
        console.log('[SSE] 连接已打开')
      },
      onError: (e, attempt, delay) => {
        console.warn(`[SSE] 连接错误，准备第${attempt}次重连，延迟 ${delay}ms`, e)
        setCheckingLogin(false)
      },
      listeners: [
        {
          name: 'connected',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              setIsLoggedIn(data.bossLoggedIn || false)
              setCheckingLogin(false)
            } catch (error) {
              console.error('[SSE] 解析连接消息失败:', error)
            }
          },
        },
        {
          name: 'login-status',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              if (data.platform === 'boss') {
                setIsLoggedIn(data.isLoggedIn)
                setCheckingLogin(false)
              }
            } catch (error) {
              console.error('[SSE] 解析登录状态消息失败:', error)
            }
          },
        },
        { name: 'ping', handler: () => {} },
      ],
    })

    return () => {
      client.close()
    }
  }, [])

  const fetchAllData = async () => {
    try {
      const response = await fetch('http://localhost:8888/api/boss/config')
      const data = await response.json()

      console.log('Fetched data:', data)
      console.log('Blacklist:', data.blacklist)

      if (data.config) {
        // 规范化城市编码：后端可能返回单值或括号列表，此处取第一个值用于下拉回显
        const normalizeCityCode = (raw?: string): string => {
          if (!raw) return ''
          const list = parseListString(raw)
          if (list.length > 0) return list[0]
          return raw
        }
        // 规范化职位类型：后端可能返回单值或括号列表，此处取第一个值用于下拉回显
        const normalizeJobType = (raw?: string): string => {
          if (!raw) return ''
          const list = parseListString(raw)
          if (list.length > 0) return list[0]
          return raw
        }
        setConfig({
          ...data.config,
          cityCode: normalizeCityCode(data.config.cityCode),
          jobType: normalizeJobType(data.config.jobType),
        })
        // 将后端存储的关键词（可能是 JSON 数组或括号列表）转为展示用逗号分隔文本
        const toDisplayKeywords = (raw?: string): string => {
          if (!raw) return ''
          const s = raw.trim()
          // 尝试作为 JSON 数组解析
          if (s.startsWith('[') && s.endsWith(']')) {
            try {
              const arr = JSON.parse(s)
              if (Array.isArray(arr)) {
                return arr.map((v) => String(v).trim()).filter((v) => v.length > 0).join(', ')
              }
            } catch (_) {
              // 非严格 JSON，如 [a,b]，走拆括号与逗号分隔
              const inner = s.slice(1, -1)
              return inner
                .split(',')
                .map((v) => v.trim().replace(/^"|"$/g, ''))
                .filter((v) => v.length > 0)
                .join(', ')
            }
          }
          // 普通文本：直接返回，去掉多余空格
          return s
        }
        setKeywordsDisplay(toDisplayKeywords(data.config.keywords))
        // 解析括号列表为数组
        setSelectedIndustry(parseListString(data.config.industry))
        setSelectedExperience(parseListString(data.config.experience))
        setSelectedDegree(parseListString(data.config.degree))
        setSelectedScale(parseListString(data.config.scale))
        setSelectedStage(parseListString(data.config.stage))
        setSelectedSalary(parseListString(data.config.salary))
      }
      if (data.options) {
        // 按 sort_order 或固定名称顺序排序；都没有时按名称兜底
        const CITY_ORDER = [
          // 顶层：全国 + 一线（北上广深）
          '全国','北京','上海','广州','深圳',
          // 准一线（图片顺序，从上到下）
          '杭州','成都','南京',
          '武汉','苏州','重庆','天津',
          '长沙','青岛','宁波','无锡',
          '西安','郑州','合肥','厦门','东莞',
          // 二线（图片顺序）
          '济南','福州','佛山','昆明','大连','沈阳','常州','哈尔滨','南昌','泉州',
          '南通','烟台','温州','贵阳','南宁','石家庄','长春','嘉兴','珠海','太原',
          '绍兴','金华','潍坊','徐州','惠州','台州','扬州','中山','乌鲁木齐','兰州',
          // 省会补充（图片底部出现的省会/直辖市）
          '海口','呼和浩特','银川'
        ]
        const orderMap = new Map<string, number>(CITY_ORDER.map((n, i) => [n, i + 1]))
        // 城市排序：仅在后端提供 sortOrder 时按其排序；否则保留后端返回顺序
        const cityList = data.options.city || []
        const cityHasOrder = cityList.some((o: BossOption) => o.sortOrder != null || o.sort_order != null)
        let sortedCity = cityHasOrder
          ? [...cityList]
              .map((o, idx) => ({ o, idx }))
              .sort((a, b) => {
                const ar = a.o.sortOrder ?? a.o.sort_order
                const br = b.o.sortOrder ?? b.o.sort_order
                if (ar == null && br == null) return a.idx - b.idx // 都无排序，保持原序
                if (ar == null) return 1 // 无排序的排在已排序之后，保持原序
                if (br == null) return -1
                if (ar !== br) return ar - br
                return a.idx - b.idx // 稳定排序
              })
              .map(({ o }) => o)
          : cityList

        // 兜底：如果后端未返回『不限』（code='0'），前端补充一个，确保可选
        const hasUnlimitedCity = sortedCity.some((c: BossOption) => c.code === '0' || c.name === '不限')
        if (!hasUnlimitedCity) {
          sortedCity = [{ id: -1, type: 'city', name: '不限', code: '0', sortOrder: 0 }, ...sortedCity]
        }

        // 行业排序：仅在后端提供 sortOrder 时按其排序；否则保留后端返回顺序
        const industryList = data.options.industry || []
        const industryHasOrder = industryList.some((o: BossOption) => o.sortOrder != null || o.sort_order != null)
        const sortedIndustry = industryHasOrder
          ? [...industryList]
              .map((o, idx) => ({ o, idx }))
              .sort((a, b) => {
                const ar = a.o.sortOrder ?? a.o.sort_order
                const br = b.o.sortOrder ?? b.o.sort_order
                if (ar == null && br == null) return a.idx - b.idx
                if (ar == null) return 1
                if (br == null) return -1
                if (ar !== br) return ar - br
                return a.idx - b.idx
              })
              .map(({ o }) => o)
          : industryList

        setOptions({
          ...data.options,
          city: sortedCity,
          industry: sortedIndustry,
        })

        // 将配置中的中文值映射为代码，用于UI显示与选择匹配
        const toCodes = (opts: BossOption[], items: string[]) => {
          const codeSet = new Set(opts.map(o => o.code))
          return items.map(it => {
            if (codeSet.has(it)) return it
            const byName = opts.find(o => o.name === it)
            return byName ? byName.code : it
          })
        }

        // 城市：若当前为中文名，转换为对应的 code 以在下拉中回显
        const currentCityRaw = data.config?.cityCode || ''
        const currentCityHead = (() => {
          const list = parseListString(currentCityRaw)
          return list.length > 0 ? list[0] : currentCityRaw
        })()
        const cityMatchByCode = sortedCity.find((c: BossOption) => c.code === currentCityHead)
        const cityMatchByName = sortedCity.find((c: BossOption) => c.name === currentCityHead)
        const normalizedCityCode = cityMatchByCode ? cityMatchByCode.code : (cityMatchByName ? cityMatchByName.code : '0')
        setConfig(prev => ({ ...prev, cityCode: normalizedCityCode }))

        // 职位类型：若当前为中文名，转换为对应的 code 以在下拉中回显
        const currentJobTypeRaw = data.config?.jobType || ''
        const currentJobTypeHead = (() => {
          const list = parseListString(currentJobTypeRaw)
          return list.length > 0 ? list[0] : currentJobTypeRaw
        })()
        const jobTypeMatchByCode = (data.options.jobType || []).find((t: BossOption) => t.code === currentJobTypeHead)
        const jobTypeMatchByName = (data.options.jobType || []).find((t: BossOption) => t.name === currentJobTypeHead)
        const normalizedJobType = jobTypeMatchByCode ? jobTypeMatchByCode.code : (jobTypeMatchByName ? jobTypeMatchByName.code : '')
        setConfig(prev => ({ ...prev, jobType: normalizedJobType }))

        // HR活跃过滤开关：后端为 0/1，前端直接回显为数字
        const normalizedFilterDeadHr = (data.config?.filterDeadHr ?? 0)
        setConfig(prev => ({ ...prev, filterDeadHr: normalizedFilterDeadHr }))

        // 其它多选选项：将中文名称转换为代码以匹配 MultiSelect 的 selected
        setSelectedIndustry(toCodes(data.options.industry || [], parseListString(data.config?.industry)))
        setSelectedExperience(toCodes(data.options.experience || [], parseListString(data.config?.experience)))
        setSelectedDegree(toCodes(data.options.degree || [], parseListString(data.config?.degree)))
        setSelectedScale(toCodes(data.options.scale || [], parseListString(data.config?.scale)))
        setSelectedStage(toCodes(data.options.stage || [], parseListString(data.config?.stage)))
        setSelectedSalary(toCodes(data.options.salary || [], parseListString(data.config?.salary)))
      }
      if (data.blacklist) {
        // 兼容处理：将type为'boss'的旧数据视为'company'
        const normalizedBlacklist = data.blacklist.map((item: BlacklistItem) => ({
          ...item,
          type: item.type === 'boss' ? 'company' : item.type
        }))
        console.log('Normalized blacklist:', normalizedBlacklist)
        setBlacklist(normalizedBlacklist)
      }
    } catch (error) {
      console.error('Failed to fetch data:', error)
    } finally {
      setLoading(false)
    }
  }

  // 工具：解析括号列表字符串为数组，如 "[a,b]" 或 "a,b"
  const parseListString = (raw?: string): string[] => {
    if (!raw) return []
    let s = raw.trim()
    if (s.startsWith('[') && s.endsWith(']')) {
      s = s.slice(1, -1)
    }
    if (!s) return []
    return s
      .split(',')
      .map(v => v.trim().replace(/^"|"$/g, ''))
      .filter(v => v.length > 0)
  }

  // 工具：将数组转为括号列表字符串
  const toBracketList = (list: string[]): string => {
    if (!list || list.length === 0) return ''
    return `[${list.join(',')}]`
  }

  const handleSave = async (silent: boolean = false, overrides?: Partial<BossConfig>) => {
    try {
      // 组装要保存的负载：多选使用括号列表
      const payload: BossConfig = {
        ...config,
        // 覆盖字段（用于失焦时使用当前控件值，避免异步状态滞后）
        ...(overrides || {}),
        // 关键词：前端发送逗号分隔的纯文本，后端统一组装为 JSON 列表
        keywords: keywordsDisplay,
        industry: toBracketList(selectedIndustry),
        experience: toBracketList(selectedExperience),
        degree: toBracketList(selectedDegree),
        scale: toBracketList(selectedScale),
        stage: toBracketList(selectedStage),
        salary: toBracketList(selectedSalary),
      }
      const response = await fetch('http://localhost:8888/api/boss/config', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      })

      if (response.ok) {
        // 统一保存 Cookie（Boss）
        try {
          await fetch('http://localhost:8888/api/cookie/save?platform=boss', { method: 'POST' })
        } catch (e) {
          console.warn('保存 Cookie 失败（Boss）:', e)
        }

        fetchAllData()
        if (!silent) {
          setSaveResult({ success: true, message: '保存成功，配置与Cookie已更新。' })
          setShowSaveDialog(true)
        }
      } else {
        // 保存失败：不弹框，记录日志
        console.warn('保存失败：后端返回非 2xx 状态')
        if (!silent) {
          setSaveResult({ success: false, message: '保存失败：后端返回异常状态。' })
          setShowSaveDialog(true)
        }
      }
    } catch (error) {
      console.error('Failed to save config:', error)
      // 保存失败：不弹框
      if (!silent) {
        setSaveResult({ success: false, message: '保存失败：网络或服务异常。' })
        setShowSaveDialog(true)
      }
    }
  }

  const handleAddBlacklist = async () => {
    if (!newBlacklistKeyword.trim()) {
      // 输入为空：不弹框，直接返回
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
          type: blacklistType, // 使用选中的类型
        }),
      })

      if (response.ok) {
        setNewBlacklistKeyword('')
        fetchAllData()
      } else {
        // 添加失败：不弹框
        console.warn('添加黑名单失败：后端返回非 2xx 状态')
      }
    } catch (error) {
      console.error('Failed to add blacklist:', error)
      // 添加失败：不弹框
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
        // 删除失败：不弹框
        console.warn('删除黑名单失败：后端返回非 2xx 状态')
      }
    } catch (error) {
      console.error('Failed to delete blacklist:', error)
      // 删除失败：不弹框
    }
  }

  const handleStartDelivery = async () => {
    try {
      setIsDelivering(true)
      const response = await fetch('http://localhost:8888/api/boss/start', {
        method: 'POST',
      })
      const data = await response.json()

      if (data.success) {
        // 启动成功：不弹框
      } else {
        // 启动失败：不弹框
        console.warn('启动失败：', data.message)
        setIsDelivering(false)
      }
    } catch (error) {
      console.error('Failed to start delivery:', error)
      // 启动失败：不弹框
      setIsDelivering(false)
    }
  }

  const handleStopDelivery = async () => {
    try {
      const response = await fetch('http://localhost:8888/api/boss/stop', {
        method: 'POST',
      })
      const data = await response.json()

      if (data.success) {
        // 停止成功：不弹框
        setIsDelivering(false)
      } else {
        // 停止失败：不弹框
        console.warn('停止失败：', data.message)
      }
    } catch (error) {
      console.error('Failed to stop delivery:', error)
      // 停止失败：不弹框
    }
  }

  const triggerLogout = async () => {
    try {
      const response = await fetch('http://localhost:8888/api/boss/logout', { method: 'POST' })
      const data = await response.json()
      if (data.success) {
        setIsLoggedIn(false)
        setIsDelivering(false)
        console.info('已退出登录，数据库Cookie已置空')
        setLogoutResult({ success: true, message: '已退出登录，Cookie已清空。' })
        setShowLogoutResultDialog(true)
      } else {
        console.warn('退出登录失败：', data.message)
        setLogoutResult({ success: false, message: `退出登录失败：${data.message || '服务返回异常。'}` })
        setShowLogoutResultDialog(true)
      }
    } catch (error) {
      console.error('Failed to logout:', error)
      setLogoutResult({ success: false, message: '退出登录失败：网络或服务异常。' })
      setShowLogoutResultDialog(true)
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
        actions={
          <div className="flex items-center gap-2">
            {checkingLogin ? (
              <Button size="sm" disabled className="rounded-full bg-gray-300 text-gray-600 cursor-not-allowed px-4 shadow">
                <BiPlay className="mr-1" /> 检查登录中...
              </Button>
            ) : !isLoggedIn ? (
              <Button size="sm" disabled className="rounded-full bg-gray-300 text-gray-600 cursor-not-allowed px-4 shadow">
                <BiPlay className="mr-1" /> 请先登录Boss
              </Button>
            ) : isDelivering ? (
              <Button onClick={handleStopDelivery} size="sm" className="rounded-full bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
                <BiStop className="mr-1" /> 停止投递
              </Button>
            ) : (
              <Button onClick={handleStartDelivery} size="sm" className="rounded-full bg-gradient-to-r from-teal-500 to-green-500 hover:from-teal-600 hover:to-green-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
                <BiPlay className="mr-1" /> 开始投递
              </Button>
            )}
            <Button onClick={() => setShowLogoutDialog(true)} size="sm" className="rounded-full bg-gradient-to-r from-red-500 to-pink-500 hover:from-red-600 hover:to-pink-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
              <BiLogOut className="mr-1" /> 退出登录
            </Button>
            <Button onClick={() => handleSave(false)} size="sm" className="rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-600 hover:to-indigo-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
              <BiSave className="mr-1" /> 保存配置
            </Button>
          </div>
        }
      />

      <Tabs defaultValue="config" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="config">平台配置</TabsTrigger>
          <TabsTrigger value="analytics">投递分析</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="space-y-6 mt-6">
          {/* 平台说明 */}
          <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <BiBriefcase className="text-primary" />
                Boss直聘平台说明
              </CardTitle>
              <CardDescription>登录与投递操作提示</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <p className="text-sm text-muted-foreground">请在浏览器标签页中登录 Boss 直聘平台，登录成功后系统会自动检测登录状态。</p>
                <p className="text-sm text-muted-foreground">登录成功后，点击“开始投递”按钮启动自动投递任务。</p>
                <p className="text-sm text-muted-foreground">点击“保存配置”按钮可手动保存当前登录相关信息到数据库。</p>
              </div>
            </CardContent>
          </Card>

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
                    value={keywordsDisplay}
                    onChange={(e) => setKeywordsDisplay(e.target.value)}
                    placeholder="例如：Java开发工程师"
                  />
                  <p className="text-xs text-muted-foreground">职位搜索的关键词</p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="city">工作城市</Label>
                  <Select
                    id="city"
                    value={config.cityCode || ''}
                    onChange={(e) => setConfig({ ...config, cityCode: e.target.value })}
                  >
                    {options.city.map((city) => (
                      <option key={city.id} value={city.code}>
                        {city.name}
                      </option>
                    ))}
                </Select>
                <p className="text-xs text-muted-foreground">目标工作城市（按设定顺序显示）</p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="jobType">职位类型</Label>
                <Select
                  id="jobType"
                  value={config.jobType || ''}
                  onChange={(e) => setConfig({ ...config, jobType: e.target.value })}
                >
                  {options.jobType.map((type) => (
                    <option key={type.id} value={type.code}>
                      {type.name}
                    </option>
                  ))}
                </Select>
                <p className="text-xs text-muted-foreground">选择职位类型</p>
              </div>

              <div className="space-y-2">
                <Label>公司行业</Label>
                <MultiSelect
                  options={options.industry}
                  selected={selectedIndustry}
                    onChange={setSelectedIndustry}
                    placeholder="选择公司行业"
                  />
                  <p className="text-xs text-muted-foreground">可多选</p>
                </div>
              
              {/* HR活跃过滤开关 */}
              <div className="space-y-2">
                <Label htmlFor="filterDeadHr">HR活跃过滤</Label>
                <Select
                  id="filterDeadHr"
                  value={String(config.filterDeadHr ?? 0)}
                  onChange={(e) => setConfig({ ...config, filterDeadHr: Number(e.target.value) })}
                >
                  <option value="0">关闭</option>
                  <option value="1">开启</option>
                </Select>
                <p className="text-xs text-muted-foreground">开启后将过滤活跃状态包含“年”的HR，但仍保存数据。</p>
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
            <CardDescription>设置薪资待遇和工作经验要求</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label>薪资待遇</Label>
                <MultiSelect
                  options={options.salary}
                  selected={selectedSalary}
                  onChange={setSelectedSalary}
                  placeholder="选择薪资待遇"
                />
                <p className="text-xs text-muted-foreground">选项来源：字典表 type=salary（可多选）</p>
              </div>
              <div className="space-y-2">
                <Label>工作经验</Label>
                <MultiSelect
                  options={options.experience}
                  selected={selectedExperience}
                  onChange={setSelectedExperience}
                  placeholder="选择工作经验"
                />
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
                <Label>学历要求</Label>
                <MultiSelect
                  options={options.degree}
                  selected={selectedDegree}
                  onChange={setSelectedDegree}
                  placeholder="选择学历要求"
                />
              </div>

              <div className="space-y-2">
                <Label>公司规模</Label>
                <MultiSelect
                  options={options.scale}
                  selected={selectedScale}
                  onChange={setSelectedScale}
                  placeholder="选择公司规模"
                />
              </div>

              <div className="space-y-2">
                <Label>融资阶段</Label>
                <MultiSelect
                  options={options.stage}
                  selected={selectedStage}
                  onChange={setSelectedStage}
                  placeholder="选择融资阶段"
                />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 黑名单管理 */}
        <Card className="animate-in fade-in slide-in-from-bottom-8 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiSearch className="text-primary" />
              黑名单管理 ({blacklist.length} 条)
            </CardTitle>
            <CardDescription>添加或删除不想投递的公司、职位或HR</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-6">
              {/* 添加黑名单 */}
              <div className="flex gap-2">
                <Select
                  value={blacklistType}
                  onChange={(e) => setBlacklistType(e.target.value)}
                  className="w-32"
                >
                  <option value="company">公司</option>
                  <option value="job">岗位</option>
                  <option value="recruiter">HR</option>
                </Select>
                <Input
                  value={newBlacklistKeyword}
                  onChange={(e) => setNewBlacklistKeyword(e.target.value)}
                  placeholder={`输入${blacklistType === 'company' ? '公司名称' : blacklistType === 'job' ? '岗位关键词' : 'HR职位'}关键词`}
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

              {/* 黑名单列表 - 按类型分组显示 */}
              <div className="space-y-6">
                {/* 公司黑名单 */}
                <div>
                  <h3 className="text-sm font-semibold mb-3 flex items-center gap-2">
                    <BiBuilding className="text-orange-500" />
                    <span>公司黑名单 ({blacklist.filter(item => item.type === 'company').length})</span>
                  </h3>
                  <div className="space-y-2">
                    {blacklist.filter(item => item.type === 'company').length === 0 ? (
                      <div className="text-center py-4 text-muted-foreground bg-muted/30 rounded-lg">
                        <p className="text-xs">暂无公司黑名单</p>
                      </div>
                    ) : (
                      blacklist.filter(item => item.type === 'company').map((item) => (
                        <div
                          key={item.id}
                          className="flex items-center justify-between p-3 bg-orange-50 dark:bg-orange-950/20 rounded-lg border border-orange-200 dark:border-orange-800"
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
                      ))
                    )}
                  </div>
                </div>

                {/* 岗位黑名单 */}
                <div>
                  <h3 className="text-sm font-semibold mb-3 flex items-center gap-2">
                    <BiBriefcase className="text-blue-500" />
                    <span>岗位黑名单 ({blacklist.filter(item => item.type === 'job').length})</span>
                  </h3>
                  <div className="space-y-2">
                    {blacklist.filter(item => item.type === 'job').length === 0 ? (
                      <div className="text-center py-4 text-muted-foreground bg-muted/30 rounded-lg">
                        <p className="text-xs">暂无岗位黑名单</p>
                      </div>
                    ) : (
                      blacklist.filter(item => item.type === 'job').map((item) => (
                        <div
                          key={item.id}
                          className="flex items-center justify-between p-3 bg-blue-50 dark:bg-blue-950/20 rounded-lg border border-blue-200 dark:border-blue-800"
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
                      ))
                    )}
                  </div>
                </div>

                {/* HR黑名单 */}
                <div>
                  <h3 className="text-sm font-semibold mb-3 flex items-center gap-2">
                    <BiSearch className="text-green-500" />
                    <span>HR黑名单 ({blacklist.filter(item => item.type === 'recruiter').length})</span>
                  </h3>
                  <div className="space-y-2">
                    {blacklist.filter(item => item.type === 'recruiter').length === 0 ? (
                      <div className="text-center py-4 text-muted-foreground bg-muted/30 rounded-lg">
                        <p className="text-xs">暂无HR黑名单</p>
                      </div>
                    ) : (
                      blacklist.filter(item => item.type === 'recruiter').map((item) => (
                        <div
                          key={item.id}
                          className="flex items-center justify-between p-3 bg-green-50 dark:bg-green-950/20 rounded-lg border border-green-200 dark:border-green-800"
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
                      ))
                    )}
                  </div>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        
        </TabsContent>

        <TabsContent value="analytics" className="space-y-6 mt-6">
          <AnalysisContent />
        </TabsContent>
      </Tabs>

      {/* 统计卡片已移除 */}
      {/* 退出确认弹框 */}
      {showLogoutDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border border-gray-200 dark:border-neutral-800 animate-in fade-in zoom-in-95">
            <Card className="border-0">
              <CardHeader className="pb-2">
                <CardTitle className="text-lg flex items-center gap-2">
                  <BiLogOut className="text-red-500" />
                  确认退出登录
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-0">
                <p className="text-sm text-muted-foreground mb-4">退出后将清除Cookie并切换为未登录状态。</p>
                <div className="flex justify-end gap-2">
                  <Button
                    variant="ghost"
                    onClick={() => setShowLogoutDialog(false)}
                    className="rounded-full px-4"
                  >
                    取消
                  </Button>
                  <Button
                    onClick={async () => {
                      await triggerLogout()
                      setShowLogoutDialog(false)
                    }}
                    className="rounded-full bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white px-4"
                  >
                    确认退出
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      {/* 退出登录结果弹框 */}
      {showLogoutResultDialog && logoutResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" role="dialog" aria-modal="true">
          <div className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border border-gray-200 dark:border-neutral-800 animate-in fade-in zoom-in-95">
            <Card className="border-0">
              <CardHeader className="pb-2">
                <CardTitle className="text-lg flex items-center gap-2">
                  <BiLogOut className={logoutResult.success ? 'text-green-500' : 'text-red-500'} />
                  {logoutResult.success ? '退出登录成功' : '退出登录失败'}
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-0">
                <p className="text-sm text-muted-foreground mb-4">{logoutResult.message}</p>
                <div className="flex justify-end gap-2">
                  <Button
                    onClick={() => setShowLogoutResultDialog(false)}
                    className={`rounded-full px-4 ${logoutResult.success ? 'bg-gradient-to-r from-green-500 to-emerald-600 hover:from-green-600 hover:to-emerald-700 text-white' : 'bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white'}`}
                  >
                    知道了
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}

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

// 多选下拉组件（简单版），按代码选择并显示名称
function MultiSelect({
  options,
  selected,
  onChange,
  placeholder,
  onClose,
}: {
  options: BossOption[]
  selected: string[]
  onChange: (v: string[]) => void
  placeholder?: string
  onClose?: () => void
}) {
  const [open, setOpen] = useState(false)
  const [mounted, setMounted] = useState(false)
  const wrapperRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0, width: 0 })

  // 确保组件已挂载（解决 SSR 问题）
  useEffect(() => {
    setMounted(true)
  }, [])

  // 计算下拉框位置
  const updatePosition = useCallback(() => {
    if (buttonRef.current) {
      const rect = buttonRef.current.getBoundingClientRect()
      setDropdownPosition({
        top: rect.bottom + 8,
        left: rect.left,
        width: rect.width,
      })
    }
  }, [])

  // 打开时计算位置
  useEffect(() => {
    if (open) {
      updatePosition()
      // 监听滚动和窗口大小变化，更新位置
      const handleUpdate = () => updatePosition()
      window.addEventListener('scroll', handleUpdate, true)
      window.addEventListener('resize', handleUpdate)
      return () => {
        window.removeEventListener('scroll', handleUpdate, true)
        window.removeEventListener('resize', handleUpdate)
      }
    }
  }, [open, updatePosition])

  // 点击组件外部或焦点移出时关闭下拉
  useEffect(() => {
    const handleOutsideClick = (e: MouseEvent) => {
      if (!open) return
      const target = e.target as Node
      // 检查点击是否在按钮或下拉框内
      const clickedButton = wrapperRef.current?.contains(target)
      const clickedDropdown = dropdownRef.current?.contains(target)

      console.log('[MultiSelect] 外部点击检测', {
        clickedButton,
        clickedDropdown,
        targetElement: (target as HTMLElement)?.tagName,
        targetClass: (target as HTMLElement)?.className
      })

      if (!clickedButton && !clickedDropdown) {
        console.log('[MultiSelect] 检测到外部点击，关闭下拉框')
        setOpen(false)
        onClose?.()
      } else {
        console.log('[MultiSelect] 点击在组件内部，保持打开')
      }
    }
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        console.log('[MultiSelect] ESC 键关闭')
        setOpen(false)
        onClose?.()
      }
    }

    if (open) {
      console.log('[MultiSelect] 下拉框打开，注册监听器')
      // 使用 setTimeout 确保 DOM 已更新
      setTimeout(() => {
        document.addEventListener('mousedown', handleOutsideClick)
        document.addEventListener('keydown', handleEscape)
      }, 0)
    }

    return () => {
      if (open) {
        console.log('[MultiSelect] 移除监听器')
      }
      document.removeEventListener('mousedown', handleOutsideClick)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open, onClose])

  const toggle = (code: string) => {
    console.log('[MultiSelect] toggle 被调用', { code, currentSelected: selected })
    if (selected.includes(code)) {
      const newSelected = selected.filter((c) => c !== code)
      console.log('[MultiSelect] 取消选择，新值:', newSelected)
      onChange(newSelected)
    } else {
      const newSelected = [...selected, code]
      console.log('[MultiSelect] 添加选择，新值:', newSelected)
      onChange(newSelected)
    }
  }

  const selectedNames = options
    .filter((o) => selected.includes(o.code))
    .map((o) => o.name)

  return (
    <div className="relative" ref={wrapperRef}>
      <button
        ref={buttonRef}
        type="button"
        onClick={() => { const next = !open; setOpen(next); if (!next) onClose?.() }}
        className="flex h-10 w-full items-center justify-between rounded-full border border-white/20 bg-white/10 px-4 py-2 text-sm shadow-[inset_0_1px_0_rgba(255,255,255,.25)] transition-all duration-200 hover:bg-white/15 hover:shadow-md focus:outline-none focus:ring-2 focus:ring-cyan-400/40 focus:ring-offset-0"
      >
        <span className="truncate text-sm">
          {selectedNames.length > 0 ? selectedNames.join('，') : (placeholder || '请选择')}
        </span>
        <span className={`ml-2 text-xs text-muted-foreground transition-transform duration-200 ${open ? 'rotate-180' : ''}`}>▼</span>
      </button>
      {open && mounted && createPortal(
        <div
          ref={dropdownRef}
          className="dropdown-panel p-2"
          style={{
            top: `${dropdownPosition.top}px`,
            left: `${dropdownPosition.left}px`,
            width: `${dropdownPosition.width}px`,
          }}
        >
          <div className="flex flex-col gap-2">
            {options.map((opt) => {
              const checked = selected.includes(opt.code)
              return (
                <div
                  key={opt.id}
                  className={`group inline-flex items-center justify-between gap-3 rounded-full px-3 py-2 cursor-pointer transition-all border ${checked ? 'border-teal-300/60 bg-gradient-to-r from-teal-500/12 to-cyan-500/12 text-teal-900 dark:text-teal-200 shadow' : 'border-white/20 bg-white/8 text-foreground hover:bg-white/12'}`}
                  onClick={(e) => {
                    console.log('[MultiSelect] div 被点击', {
                      optionCode: opt.code,
                      optionName: opt.name,
                      currentChecked: checked
                    })
                    toggle(opt.code)
                  }}
                >
                  <span className="flex items-center gap-3">
                    <span className={`inline-flex h-4 w-4 items-center justify-center rounded-md border border-white/30 bg-white/10 shadow-inner transition-all ${checked ? 'bg-teal-400/60 border-teal-300/80' : ''}`}></span>
                    <span className="text-sm truncate">{opt.name}</span>
                  </span>
                </div>
              )
            })}
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}
