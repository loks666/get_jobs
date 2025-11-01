'use client'

import { BiBriefcase, BiInfoCircle } from 'react-icons/bi'

export default function BossPage() {
  return (
    <div>
      <h1 className="text-3xl font-bold text-gray-800 mb-6">
        <BiBriefcase className="inline mr-2" />Boss直聘 配置
      </h1>

      <div className="config-section">
        <div className="flex items-start gap-4">
          <BiInfoCircle className="text-blue-500 text-2xl flex-shrink-0 mt-1" />
          <div>
            <h3 className="text-lg font-semibold text-gray-800 mb-2">平台配置开发中</h3>
            <p className="text-gray-600 mb-4">
              Boss直聘平台的配置页面正在开发中。您将能够在此页面配置：
            </p>
            <ul className="list-disc list-inside text-gray-600 space-y-2">
              <li>搜索关键词和职位筛选</li>
              <li>目标行业和工作城市</li>
              <li>薪资范围和学历要求</li>
              <li>公司规模和融资阶段</li>
              <li>自动投递设置</li>
            </ul>
          </div>
        </div>
      </div>

      <div className="bg-gradient-to-r from-purple-50 to-blue-50 p-6 rounded-lg border border-purple-200">
        <h4 className="text-lg font-semibold text-purple-800 mb-2">即将上线</h4>
        <p className="text-purple-700">
          完整的Boss直聘配置功能即将推出，敬请期待！
        </p>
      </div>
    </div>
  )
}
