import DefaultTheme from 'vitepress/theme'
import { h } from 'vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  Layout() {
    return h(DefaultTheme.Layout, null, {
      // 在正文底部注入不蒜子访问量统计展示与评论位插槽
      'doc-after': () => h('div', { class: 'busuanzi-stats-container' }, [
        h('span', { id: 'busuanzi_container_page_pv', style: 'display:inline; margin-right: 16px;' }, [
          h('span', { class: 'stat-label' }, '👀 本文阅读量: '),
          h('span', { id: 'busuanzi_value_page_pv', class: 'stat-value' }, '—')
        ]),
        h('span', { id: 'busuanzi_container_site_pv', style: 'display:inline; margin-right: 16px;' }, [
          h('span', { class: 'stat-label' }, '🚀 全站总访问量: '),
          h('span', { id: 'busuanzi_value_site_pv', class: 'stat-value' }, '—')
        ]),
        h('span', { id: 'busuanzi_container_site_uv', style: 'display:inline;' }, [
          h('span', { class: 'stat-label' }, '👥 独立访客: '),
          h('span', { id: 'busuanzi_value_site_uv', class: 'stat-value' }, '—')
        ])
      ])
    })
  }
}
