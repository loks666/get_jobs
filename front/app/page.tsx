'use client'

import { useState } from 'react'
import { BiSave, BiRefresh, BiServer, BiData, BiFile } from 'react-icons/bi'

export default function Home() {
  const [config, setConfig] = useState({
    appName: 'get_jobs',
    profile: 'dev',
    serverPort: '8888',
    dbUrl: 'jdbc:sqlite:./db/config.db',
    dataDbUrl: 'jdbc:sqlite:./db/data.db',
    logLevel: 'INFO',
    logFile: './target/logs/get-jobs.log',
  })

  const handleSave = () => {
    console.log('Saving config:', config)
    alert('配置已保存！')
  }

  const handleReset = () => {
    if (confirm('确定要重置配置吗？')) {
      window.location.reload()
    }
  }

  return (
    <div>
      <h1 className="text-3xl font-bold text-gray-800 mb-6">主配置 (application.yaml)</h1>

      <div className="config-section">
        <h3><BiServer className="inline mr-2" />应用配置</h3>
        <div className="config-grid">
          <div className="form-group">
            <label className="form-label">应用名称</label>
            <input
              type="text"
              value={config.appName}
              onChange={(e) => setConfig({ ...config, appName: e.target.value })}
              className="form-control"
            />
          </div>

          <div className="form-group">
            <label className="form-label">环境配置</label>
            <select
              value={config.profile}
              onChange={(e) => setConfig({ ...config, profile: e.target.value })}
              className="form-control"
            >
              <option value="dev">开发环境 (dev)</option>
              <option value="prod">生产环境 (prod)</option>
              <option value="test">测试环境 (test)</option>
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">服务器端口</label>
            <input
              type="number"
              value={config.serverPort}
              onChange={(e) => setConfig({ ...config, serverPort: e.target.value })}
              className="form-control"
            />
            <small className="text-muted">应用运行的端口号</small>
          </div>
        </div>
      </div>

      <div className="config-section">
        <h3><BiData className="inline mr-2" />数据库配置</h3>
        <div className="config-grid">
          <div className="form-group">
            <label className="form-label">配置数据库路径</label>
            <input
              type="text"
              value={config.dbUrl}
              onChange={(e) => setConfig({ ...config, dbUrl: e.target.value })}
              className="form-control"
            />
            <small className="text-muted">SQLite配置数据库文件路径</small>
          </div>

          <div className="form-group">
            <label className="form-label">数据数据库路径</label>
            <input
              type="text"
              value={config.dataDbUrl}
              onChange={(e) => setConfig({ ...config, dataDbUrl: e.target.value })}
              className="form-control"
            />
            <small className="text-muted">SQLite数据库文件路径</small>
          </div>
        </div>
      </div>

      <div className="config-section">
        <h3><BiFile className="inline mr-2" />日志配置</h3>
        <div className="config-grid">
          <div className="form-group">
            <label className="form-label">日志级别</label>
            <select
              value={config.logLevel}
              onChange={(e) => setConfig({ ...config, logLevel: e.target.value })}
              className="form-control"
            >
              <option value="DEBUG">DEBUG - 调试信息</option>
              <option value="INFO">INFO - 一般信息</option>
              <option value="WARN">WARN - 警告信息</option>
              <option value="ERROR">ERROR - 错误信息</option>
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">日志文件路径</label>
            <input
              type="text"
              value={config.logFile}
              onChange={(e) => setConfig({ ...config, logFile: e.target.value })}
              className="form-control"
            />
            <small className="text-muted">日志文件存储位置</small>
          </div>
        </div>
      </div>

      <div className="flex gap-4 mt-6">
        <button onClick={handleSave} className="btn btn-primary">
          <BiSave className="inline mr-2" />保存配置
        </button>
        <button onClick={handleReset} className="btn btn-secondary">
          <BiRefresh className="inline mr-2" />重置配置
        </button>
      </div>
    </div>
  )
}
