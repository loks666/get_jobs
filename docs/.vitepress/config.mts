import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'zh-CN',
  title: 'Get Jobs - AI 找工作助手',
  description: '全平台自动投简历脚本，支持 Boss直聘、猎聘、智联、前程无忧、拉勾，内置 AI 岗位匹配与打招呼语生成。',
  
  // GitHub Pages 使用仓库路径，本地开发使用根路径。
  base: process.env.DOCS_BASE || (process.env.GITHUB_ACTIONS ? '/get_jobs/' : '/'),
  lastUpdated: true,

  vite: {
    server: {
      watch: {
        ignored: ['**/dist/**', '**/.vitepress/dist/**']
      }
    }
  },

  head: [
    ['link', { rel: 'icon', href: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><text y=".9em" font-size="90">🍀</text></svg>' }],
    ['meta', { name: 'keywords', content: 'Get Jobs, 找工作, 自动投简历, AI打招呼, Boss直聘, 猎聘, 智联招聘, 前程无忧, 开源求职助手' }],
    ['meta', { name: 'author', content: 'loks666' }],
    // 不蒜子统计脚本（防刷异步加载）
    ['script', { async: '', src: '//busuanzi.ibruce.info/busuanzi/2.3/busuanzi.pure.mini.js' }]
  ],

  themeConfig: {
    logo: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><text y=".9em" font-size="90">🍀</text></svg>',
    siteTitle: 'Get Jobs',

    // 内置本地离线全文搜索（支持中文分词与提示词）
    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: {
                buttonText: '搜索文档',
                buttonAriaLabel: '搜索文档'
              },
              modal: {
                noResultsText: '无法找到相关结果',
                resetButtonTitle: '清除查询条件',
                footer: {
                  selectText: '选择',
                  navigateText: '切换',
                  closeText: '关闭'
                }
              }
            }
          }
        }
      }
    },

    // 顶部导航栏
    nav: [
      { text: '首页', link: '/' },
      { text: '项目介绍', link: '/guide/introduction' },
      { text: '🚀 快速开始', link: '/guide/quick-start' },
      { text: '⚙️ 核心配置', link: '/guide/config' },
      { text: '❓ 常见问题', link: '/guide/faq' },
      {
        text: '相关资源',
        items: [
          { text: 'GitHub 仓库', link: 'https://github.com/loks666/get_jobs' },
          { text: 'Gitee 镜像', link: 'https://gitee.com/lok666/get_jobs' },
          { text: '界面截图', link: '/guide/screenshots' },
          { text: '社区与贡献', link: '/guide/community' },
          { text: '其他开源项目', link: '/guide/resources' },
          { text: '更新日志', link: '/guide/changelog' }
        ]
      }
    ],

    // 侧边栏结构化配置
    sidebar: [
      {
        text: '📖 指南与入门',
        items: [
          { text: '项目介绍与背景', link: '/guide/introduction' },
          { text: '5分钟快速上手', link: '/guide/quick-start' }
        ]
      },
      {
        text: '⚙️ 系统配置详解',
        items: [
          { text: '核心与各平台配置', link: '/guide/config' },
          { text: 'AI 大模型与消息推送', link: '/guide/ai-and-push' }
        ]
      },
      {
        text: '🛡️ 进阶与运维',
        items: [
          { text: '常见问题与排错指南', link: '/guide/faq' },
          { text: '更新日志与版本历史', link: '/guide/changelog' },
          { text: '界面与运行截图', link: '/guide/screenshots' },
          { text: '社区、贡献与支持', link: '/guide/community' },
          { text: '其他开源项目', link: '/guide/resources' }
        ]
      }
    ],

    // 编辑与更新配置
    editLink: {
      pattern: 'https://github.com/loks666/get_jobs/edit/main/docs/:path',
      text: '在 GitHub 上编辑此页'
    },
    lastUpdated: {
      text: '最后更新于',
      formatOptions: {
        dateStyle: 'short',
        timeStyle: 'medium'
      }
    },

    // 社交链接
    socialLinks: [
      { icon: 'github', link: 'https://github.com/loks666/get_jobs' }
    ],

    // 页脚配置
    footer: {
      message: 'Released under the PolyForm Noncommercial 1.0.0 License.',
      copyright: 'Copyright © 2024-PRESENT loks666 & Get Jobs Contributors'
    },

    docFooter: {
      prev: '上一篇',
      next: '下一篇'
    },
    outline: {
      label: '页面导航',
      level: [2, 3]
    }
  }
})
