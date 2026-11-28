import React, { useState } from 'react';
import { useBrowseDrama } from './use-browse-drama.mjs';

const names = { genre: '体裁', category_dim_theme: '题材', category_dim_role: '设定',
  category_dim_epoch: '背景', sort: '排序', gender: '受众', online_time: '上新时间', creation_status: '完结状态' };

export default function BrowsePage({ Status, PosterGrid, onOpen, onPlay }) {
  const browse = useBrowseDrama();
  const [draft, setDraft] = useState({});
  const [open, setOpen] = useState(true);
  const groups = browse.groups || [];
  const applied = groups.flatMap(group => group.items.filter(item => item.id === browse.filters[group.key] && item.id !== 'all'));
  const choose = (key, value) => {
    const next = key === 'genre' ? {} : { ...draft };
    if (value && value !== 'all') next[key] = value;
    else delete next[key];
    setDraft(next);
    if (key === 'genre') browse.apply(next);
  };
  return <>
    {!!groups.length && <div className="browse-panel">
      <button className="button browse-expand" aria-expanded={open} onClick={() => setOpen(value => !value)}>
        {open ? '收起筛选' : applied.length ? `筛选 · ${applied.map(item => item.label).join('、')}` : '展开筛选'}
      </button>
      {open && <form className="browse-controls" onSubmit={event => { event.preventDefault(); setOpen(false); browse.apply(draft); }}>
        {groups.map(group => <label key={group.key}>
          <span>{names[group.key] || group.label}</span>
          <select value={draft[group.key] || ''} disabled={browse.busy}
            onChange={event => choose(group.key, event.target.value)}>
            <option value="">全部</option>
            {group.items.filter(item => item.id !== 'all' && item.label !== '全部').map(item => <option key={item.id} value={item.id}>{item.label}</option>)}
          </select>
        </label>)}
        <button className="button" type="button" disabled={browse.busy} onClick={() => { setDraft({}); browse.apply({}); }}>重置筛选</button>
        <button className="button primary" type="submit" disabled={browse.busy}>查看结果</button>
      </form>}
    </div>}
    <Status busy={browse.busy && !browse.items.length} error={browse.error} preserve={!!browse.items.length}
      onRetry={browse.retry} empty={!browse.items.length && { title: '暂无符合筛选条件的短剧', text: '可以修改条件再试。' }}>
      <PosterGrid items={browse.items} onOpen={onOpen} onPlay={onPlay} />
    </Status>
    {browse.hasMore && !browse.error && <div className="home-more">
      <button type="button" className="button" disabled={browse.busy} onClick={browse.more}>加载更多结果</button>
    </div>}
  </>;
}
