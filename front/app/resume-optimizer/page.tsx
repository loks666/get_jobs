'use client'

import { useMemo, useState } from 'react'
import { BiBrain, BiCopy, BiDownload, BiFile, BiRefresh } from 'react-icons/bi'
import PageHeader from '../components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'

type RewriteSuggestion = {
  section?: string
  before?: string
  after?: string
  reason?: string
}

type OptimizationResult = {
  overallScore: number
  atsScore: number
  matchScore: number
  summary: string
  strengths: string[]
  risks: string[]
  missingKeywords: string[]
  recommendedKeywords: string[]
  rewriteSuggestions: RewriteSuggestion[]
  integrityWarnings: string[]
  finalResume: string
  bossGreeting: string
}

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8888'

export default function ResumeOptimizerPage() {
  const [resumeText, setResumeText] = useState('')
  const [jobDescription, setJobDescription] = useState('')
  const [targetRole, setTargetRole] = useState('')
  const [platform, setPlatform] = useState('Boss直聘')
  const [language, setLanguage] = useState('中文')
  const [extraRequirements, setExtraRequirements] = useState('')
  const [loading, setLoading] = useState(false)
  const [bossLoading, setBossLoading] = useState(false)
  const [useBossResume, setUseBossResume] = useState(true)
  const [message, setMessage] = useState('')
  const [result, setResult] = useState<OptimizationResult | null>(null)

  const canSubmit = useMemo(() => Boolean((useBossResume || resumeText.trim()) && !loading && !bossLoading), [resumeText, loading, bossLoading, useBossResume])

  const fetchBossResume = async () => {
    try {
      setBossLoading(true)
      setMessage('正在从 Boss 读取在线简历...')
      const response = await fetch(`${API_BASE}/api/resume/boss/current`)
      const data = await response.json()
      if (!response.ok || !data.success) {
        throw new Error(data.message || '读取 Boss 在线简历失败')
      }
      setResumeText(data.data?.resumeText || '')
      setUseBossResume(true)
      setPlatform('Boss直聘')
      setMessage('已读取 Boss 在线简历，可直接开始优化')
    } catch (error) {
      console.error('读取 Boss 在线简历失败:', error)
      setMessage(error instanceof Error ? error.message : '读取 Boss 在线简历失败')
    } finally {
      setBossLoading(false)
    }
  }

  const optimizeResume = async () => {
    if (!useBossResume && !resumeText.trim()) {
      setMessage('请先填写简历内容，或开启使用 Boss 在线简历')
      return
    }

    try {
      setLoading(true)
      setMessage('')
      setResult(null)
      const response = await fetch(`${API_BASE}/api/resume/optimize`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ resumeText, useBossResume, jobDescription, targetRole, platform, language, extraRequirements }),
      })
      const data = await response.json()
      if (!response.ok || !data.success) {
        throw new Error(data.message || '简历优化失败')
      }
      setResult(data.data)
      if (data.data?.sourceResumeText) {
        setResumeText(data.data.sourceResumeText)
      }
      setMessage(data.data?.resumeSource === 'boss' ? '已基于 Boss 在线简历完成优化' : '简历优化完成')
    } catch (error) {
      console.error('简历优化失败:', error)
      setMessage(error instanceof Error ? error.message : '简历优化失败，请检查 AI 配置')
    } finally {
      setLoading(false)
    }
  }

  const copyText = async (text: string, successMessage: string) => {
    if (!text) return
    await navigator.clipboard.writeText(text)
    setMessage(successMessage)
  }

  const scoreItems = result ? [
    { label: '综合评分', value: result.overallScore, color: 'from-emerald-500 to-cyan-500' },
    { label: 'ATS评分', value: result.atsScore, color: 'from-blue-500 to-indigo-500' },
    { label: '岗位匹配', value: result.matchScore, color: 'from-purple-500 to-pink-500' },
  ] : []

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiBrain />}
        title="AI简历优化"
        subtitle="基于原简历与目标岗位JD，生成匹配度、关键词差距、润色建议和投递版本"
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={fetchBossResume} disabled={loading || bossLoading} className="rounded-full">
              {bossLoading ? <BiRefresh className="animate-spin" /> : <BiDownload />}
              {bossLoading ? '读取中...' : '读取Boss简历'}
            </Button>
            <Button
              onClick={optimizeResume}
              disabled={!canSubmit}
              className="rounded-full bg-gradient-to-r from-emerald-500 to-cyan-500 text-white shadow-lg hover:from-emerald-600 hover:to-cyan-600"
            >
              {loading ? <BiRefresh className="animate-spin" /> : <BiBrain />}
              {loading ? '优化中...' : '开始优化'}
            </Button>
          </div>
        }
      />

      {message && (
        <Card className="border-cyan-500/20 bg-cyan-500/5">
          <CardContent className="pt-6 text-sm text-muted-foreground">{message}</CardContent>
        </Card>
      )}

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><BiFile className="text-primary" />输入材料</CardTitle>
            <CardDescription>不会自动编造经历；缺失信息会以待确认项输出。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label htmlFor="targetRole">目标岗位</Label>
                <Input id="targetRole" value={targetRole} onChange={(e) => setTargetRole(e.target.value)} placeholder="Java后端工程师" />
              </div>
              <div className="space-y-2">
                <Label htmlFor="platform">投递平台</Label>
                <Select id="platform" value={platform} onChange={(e) => setPlatform(e.target.value)}>
                  <option value="Boss直聘">Boss直聘</option>
                  <option value="猎聘">猎聘</option>
                  <option value="51job">51job</option>
                  <option value="智联招聘">智联招聘</option>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="language">语言</Label>
                <Select id="language" value={language} onChange={(e) => setLanguage(e.target.value)}>
                  <option value="中文">中文</option>
                  <option value="英文">英文</option>
                  <option value="中英双语">中英双语</option>
                </Select>
              </div>
            </div>

            <div className="rounded-2xl border border-cyan-500/20 bg-cyan-500/5 p-4 text-sm text-muted-foreground space-y-3">
              <label className="flex items-center gap-2 text-foreground">
                <input
                  type="checkbox"
                  checked={useBossResume}
                  onChange={(e) => setUseBossResume(e.target.checked)}
                  className="h-4 w-4"
                />
                优化时自动使用 Boss 平台当前在线简历
              </label>
              <div>开启后点击“开始优化”会实时读取 Boss 在线简历，并使用环境配置中的 `BASE_URL`、`API_KEY`、`MODEL` 调用 AI。</div>
              <Button type="button" variant="outline" size="sm" onClick={fetchBossResume} disabled={loading || bossLoading}>
                {bossLoading ? <BiRefresh className="animate-spin" /> : <BiDownload />}
                预览/刷新 Boss 简历
              </Button>
            </div>

            <div className="space-y-2">
              <Label htmlFor="resumeText">原简历内容</Label>
              <Textarea
                id="resumeText"
                value={resumeText}
                onChange={(e) => setResumeText(e.target.value)}
                placeholder="可粘贴简历文本；也可以点击读取 Boss 简历自动填充。"
                className="min-h-[260px] rounded-2xl bg-white/5"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="jobDescription">目标岗位JD（可选）</Label>
              <Textarea
                id="jobDescription"
                value={jobDescription}
                onChange={(e) => setJobDescription(e.target.value)}
                placeholder="可粘贴招聘岗位描述、任职要求、加分项；留空则进行通用/空优化。"
                className="min-h-[220px] rounded-2xl bg-white/5"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="extraRequirements">额外要求</Label>
              <Textarea
                id="extraRequirements"
                value={extraRequirements}
                onChange={(e) => setExtraRequirements(e.target.value)}
                placeholder="例如：更突出AI项目、控制在一页、避免过度包装。"
                className="min-h-[90px] rounded-2xl bg-white/5"
              />
            </div>
          </CardContent>
        </Card>

        <div className="space-y-6">
          {result ? (
            <>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {scoreItems.map((item) => (
                  <Card key={item.label}>
                    <CardContent className="pt-6">
                      <div className={`text-3xl font-bold bg-gradient-to-r ${item.color} bg-clip-text text-transparent`}>{item.value}</div>
                      <div className="mt-1 text-sm text-muted-foreground">{item.label}</div>
                    </CardContent>
                  </Card>
                ))}
              </div>

              <Card>
                <CardHeader>
                  <CardTitle>匹配总结</CardTitle>
                  <CardDescription>{result.summary}</CardDescription>
                </CardHeader>
                <CardContent className="grid grid-cols-1 md:grid-cols-2 gap-5 text-sm">
                  <TagList title="优势" items={result.strengths} tone="emerald" />
                  <TagList title="风险" items={result.risks} tone="rose" />
                  <TagList title="缺失关键词" items={result.missingKeywords} tone="amber" />
                  <TagList title="建议补充关键词" items={result.recommendedKeywords} tone="cyan" />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>改写建议</CardTitle>
                  <CardDescription>逐条展示原问题、优化版本和原因。</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  {(result.rewriteSuggestions || []).map((item, index) => (
                    <div key={`${item.section}-${index}`} className="rounded-2xl border border-white/10 bg-white/5 p-4 space-y-2 text-sm">
                      <div className="font-semibold text-primary">{item.section || `建议 ${index + 1}`}</div>
                      {item.before && <p><span className="text-muted-foreground">原句/问题：</span>{item.before}</p>}
                      {item.after && <p><span className="text-muted-foreground">建议改写：</span>{item.after}</p>}
                      {item.reason && <p><span className="text-muted-foreground">原因：</span>{item.reason}</p>}
                    </div>
                  ))}
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="flex-row items-center justify-between gap-4">
                  <div>
                    <CardTitle>润色后简历</CardTitle>
                    <CardDescription>可复制后再人工确认事实准确性。</CardDescription>
                  </div>
                  <Button variant="outline" size="sm" onClick={() => copyText(result.finalResume, '已复制润色后简历')}>
                    <BiCopy />复制
                  </Button>
                </CardHeader>
                <CardContent>
                  <pre className="whitespace-pre-wrap rounded-2xl bg-black/5 dark:bg-white/5 p-4 text-sm leading-7 max-h-[520px] overflow-auto">{result.finalResume}</pre>
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="flex-row items-center justify-between gap-4">
                  <div>
                    <CardTitle>Boss打招呼语</CardTitle>
                    <CardDescription>可直接复制到 Boss 配置的打招呼语中。</CardDescription>
                  </div>
                  <Button variant="outline" size="sm" onClick={() => copyText(result.bossGreeting, '已复制打招呼语')}>
                    <BiCopy />复制
                  </Button>
                </CardHeader>
                <CardContent>
                  <p className="rounded-2xl bg-white/5 p-4 text-sm leading-7">{result.bossGreeting}</p>
                </CardContent>
              </Card>

              {result.integrityWarnings?.length > 0 && (
                <Card className="border-amber-500/20 bg-amber-500/5">
                  <CardHeader>
                    <CardTitle>事实完整性提醒</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <ul className="list-disc pl-5 space-y-2 text-sm">
                      {result.integrityWarnings.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </CardContent>
                </Card>
              )}
            </>
          ) : (
            <Card>
              <CardHeader>
                <CardTitle>输出结果</CardTitle>
                <CardDescription>点击“开始优化”后会显示评分、关键词、改写建议和最终简历。</CardDescription>
              </CardHeader>
              <CardContent className="text-sm text-muted-foreground leading-7">
                建议先在“环境配置”中填好 `BASE_URL`、`API_KEY`、`MODEL`。目标岗位JD可留空，留空时会做通用/空优化；开启 Boss 简历后会自动读取 Boss 在线简历。
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}

function TagList({ title, items, tone }: { title: string; items: string[]; tone: 'emerald' | 'rose' | 'amber' | 'cyan' }) {
  const toneClass = {
    emerald: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300 border-emerald-500/20',
    rose: 'bg-rose-500/10 text-rose-600 dark:text-rose-300 border-rose-500/20',
    amber: 'bg-amber-500/10 text-amber-600 dark:text-amber-300 border-amber-500/20',
    cyan: 'bg-cyan-500/10 text-cyan-600 dark:text-cyan-300 border-cyan-500/20',
  }[tone]

  return (
    <div>
      <div className="mb-2 font-semibold">{title}</div>
      <div className="flex flex-wrap gap-2">
        {(items || []).length > 0 ? items.map((item) => (
          <span key={item} className={`rounded-full border px-3 py-1 ${toneClass}`}>{item}</span>
        )) : <span className="text-muted-foreground">暂无</span>}
      </div>
    </div>
  )
}
