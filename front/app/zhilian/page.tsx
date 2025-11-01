'use client'

import { BiUserCircle, BiInfoCircle } from 'react-icons/bi'

export default function ZhilianPage() {
  return (
    <div>
      <h1 className="text-3xl font-bold text-gray-800 mb-6">
        <BiUserCircle className="inline mr-2" />智联招聘 配置
      </h1>

      <div className="config-section">
        <div className="flex items-start gap-4">
          <BiInfoCircle className="text-blue-500 text-2xl flex-shrink-0 mt-1" />
          <div>
            <h3 className="text-lg font-semibold text-gray-800 mb-2">平台配置开发中</h3>
            <p className="text-gray-600 mb-4">
              智联招聘平台的配置页面正在开发中。您将能够在此页面配置各项设置。
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
