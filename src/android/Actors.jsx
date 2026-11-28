import React, { useState } from 'react';
import { ChevronRight, Search, User } from '../components/icons.jsx';
import { useAndroidDirectory } from './use-browse-drama.mjs';

const actorKey = row => row.id;
function actorItems(rows) {
  if (!Array.isArray(rows)) throw new Error('演员目录格式无效');
  return rows.filter(row => typeof row?.id === 'string' && typeof row.name === 'string' && row.name);
}

export default function Actors({ Status, onBack, onSearch }) {
  const catalog = useAndroidDirectory('actorDirectory', actorItems, actorKey);
  const [filter, setFilter] = useState('');
  const keyword = filter.trim().toLocaleLowerCase();
  const names = catalog.items.filter(row => row.name.toLocaleLowerCase().includes(keyword));
  return <section>
    <button className="back-button" onClick={onBack}><ChevronRight size={20} />返回搜索</button>
    <header className="page-heading"><h1>演员</h1></header>
    <input type="search" className="reservation-search" aria-label="筛选已加载演员" placeholder="筛选已加载演员"
      value={filter} onChange={event => setFilter(event.target.value)} />
    <Status busy={catalog.busy && !catalog.items.length} error={catalog.error} preserve={!!catalog.items.length}
      empty={!names.length && { title: keyword ? '已加载列表中没有匹配演员' : '暂未找到演员' }} onRetry={catalog.retry}>
      <div className="actor-list">{names.map(row => <button key={row.id} type="button" className="actor-row"
        aria-label={`搜索${row.name}的作品`} onClick={() => onSearch(row.name)}>
        {row.avatar ? <img className="actor-avatar" src={row.avatar} alt="" loading="lazy" /> : <span className="actor-avatar"><User size={20} /></span>}
        <span><strong>{row.name}</strong>{row.works > 0 && <small>{row.works} 部短剧</small>}</span><Search size={17} />
      </button>)}</div>
    </Status>
    {catalog.hasMore && !catalog.error && <div className="home-more">
      <button type="button" className="button" disabled={catalog.busy} onClick={catalog.more}>加载更多演员</button>
    </div>}
  </section>;
}
