import React, { useState } from 'react';
import { Search } from '../components/icons.jsx';

const settings = [
  ['updates-title', '版本更新', '更新下载 安装 版本号 apk'],
  ['settings-appearance', '外观', '浅色 深色 明暗 亮暗 主题 跟随系统'],
  ['settings-autoplay', '自动连播', '播放 连播 下一集 下一季'],
  ['settings-cuts', '跳过片头片尾', '开头 末尾 快进 播放'],
  ['settings-navigation', '底栏导航', '首页 栏目 排序 隐藏 添加 调整 入口'],
  ['account-title', '红果账号', '扫码 登录 注册 同步'],
  ['settings-announcements', '公告', '消息 提醒'],
  ['settings-cache', '播放缓存', '清理 存储 所占空间 删除'],
];

export default function SettingsSearch() {
  const [query, setQuery] = useState('');
  const search = query.trim().toLowerCase();
  const results = search ? settings.filter(([, title, words]) =>
    search.split(/\s+/).every(token => (title+words).includes(token))) : [];
  const open = id => {
    const element = document.getElementById(id);
    if (!element) return;
    element.setAttribute('tabindex', '-1');
    element.focus({ preventScroll: true });
    element.scrollIntoView({ block: 'start', behavior: 'auto' });
  };
  return <div className="settings-index">
    <div className="search-controls"><input type="search" aria-label="搜索设置" placeholder="搜索设置"
      value={query} onChange={event => setQuery(event.target.value)} /><button type="button" className="button"
      aria-label="定位首项设置" disabled={!results.length} onClick={() => open(results[0][0])}><Search size={18} /></button></div>
    {search && (results.length ? <div className="about-links" aria-label="设置搜索结果">
      {results.map(([id, title]) => <button className="button" type="button" key={id} onClick={() => open(id)}>{title}</button>)}
    </div> : <p className="account-note" role="status">没有匹配的设置</p>)}
  </div>;
}
