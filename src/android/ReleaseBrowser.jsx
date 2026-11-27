import React, { useEffect, useState } from 'react';
import { nativeCall } from './bridge.mjs';
import { parseNewReleases } from './new-releases.mjs';

export default function ReleaseBrowser({ PosterGrid, Status, onOpen, onPlay }) {
  const [today, setToday] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState({ busy: true, items: [] });
  useEffect(() => {
    let live = true;
    setState({ busy: true, items: [] });
    nativeCall('searchNewReleases', { pages: 2 }, 30000).then(result => {
      if (live) setState({ items: parseNewReleases(result.items) });
    }).catch(cause => {
      if (live) setState({ items: [], error: cause.message || '上新目录暂未加载' });
    });
    return () => { live = false; };
  }, [attempt]);
  const items = today ? state.items.filter(row => row.episodeLabel === '今日上新') : state.items;
  return <>
    <div className="section-tabs release-filters" role="group" aria-label="上新时间">
      <button type="button" aria-pressed={!today} onClick={() => setToday(false)}>近七天</button>
      <button type="button" aria-pressed={today} onClick={() => setToday(true)}>今日上新</button>
    </div>
    <Status busy={state.busy} error={state.error} onRetry={() => setAttempt(value => value + 1)}
      empty={!items.length && { title: today ? '目录暂无标为今日上新的短剧' : '暂未找到近期上新的短剧', text: '下拉浏览其他栏目或稍后重试。' }}>
      <PosterGrid items={items} onOpen={onOpen} onPlay={onPlay} />
    </Status>
  </>;
}
