import React, { useState } from 'react';
import { Bell, ChevronRight } from '../components/icons.jsx';

export function ReserveButton({ series, reservations }) {
  const saved = reservations.items.some(row => row.seriesId === series.seriesId);
  return <button className={`button${saved ? '' : ' primary'}`} type="button" aria-pressed={saved}
    disabled={reservations.busy} onClick={() => reservations.toggle(series, !saved)}>
    <Bell size={17} />{saved ? '取消预约' : '预约提醒'}
  </button>;
}

export function ReservationsPage({ reservations, onBack, onOpen, onPlay }) {
  const [view, setView] = useState('upcoming');
  const online = view === 'online', follow = view === 'follow';
  const [query, setQuery] = useState('');
  const keyword = query.trim().toLocaleLowerCase();
  const directory = follow ? reservations.follows : reservations.items.filter(row => Boolean(row.online) === online);
  const items = directory.filter(row => row.title.toLocaleLowerCase().includes(keyword));
  return <section className="reservations-page">
    <button className="back-button" onClick={onBack}><ChevronRight size={20} />返回追剧</button>
    <header className="page-heading heading-actions">
      <h1>预约与追更</h1>
      <button type="button" className="button" disabled={reservations.busy || !reservations.items.length && !reservations.follows.length}
        onClick={reservations.refresh}>{reservations.busy ? '检查中…' : '检查更新'}</button>
    </header>
    <input className="reservation-search" type="search" aria-label="搜索预约和追更的剧"
      placeholder="搜索预约和追更的剧" value={query} onChange={event => setQuery(event.target.value)} />
    <div className="section-tabs" role="group" aria-label="预约与追更类型">
      <button aria-pressed={view === 'upcoming'} onClick={() => setView('upcoming')}>待上线</button>
      <button aria-pressed={online} onClick={() => setView('online')}>已上线</button>
      <button aria-pressed={follow} onClick={() => setView('follow')}>追更</button>
    </div>
    {!reservations.notificationsAllowed && <button className="button" type="button" onClick={reservations.enable}>
      <Bell size={17} />开启系统提醒
    </button>}
    {reservations.error && <p className="text-error" role="alert">{reservations.error}</p>}
    {!items.length && <p className="result-caption">{keyword ? '暂无符合条件的短剧' : follow ? '在短剧详情开启更新提醒，即可在这里追更。' : online ? '暂无已上线的预约' : '在预约榜选择想看的短剧，预约会保存在本机。'}</p>}
    <div className="reservation-list">{items.map(row => <article key={row.seriesId} className="reservation-row">
      <button className="reservation-title" type="button" onClick={() => follow ? onOpen(row) : online ? onPlay(row) : onOpen({ ...row, upcoming: true })}>
        <strong>{row.title}</strong><span>{follow ? `已更新至第 ${row.latestEpisode} 集` : online ? '已上线 · 去观看' : row.expected || '待上线'}</span>
      </button>
      {follow
        ? <button className="button" type="button" disabled={reservations.busy}
          onClick={() => reservations.toggleFollow(row, false)}>取消追更</button>
        : <ReserveButton series={row} reservations={reservations} />}
    </article>)}</div>
  </section>;
}

export function ReservedDetail({ series, reservations, Cover, onBack, onPlay }) {
  const online = reservations.items.some(row => row.seriesId === series.seriesId && row.online);
  return <section className="reserved-detail">
    <button className="back-button" onClick={onBack}><ChevronRight size={20} />返回</button>
    <div className="detail-summary"><Cover url={series.coverUrl} />
      <div className="detail-copy">
        <h1>{series.title}</h1>
        <p className="detail-meta">{online ? '已上线' : series.expected || '即将上线'}</p>
        {online
          ? <button className="button primary" onClick={() => onPlay({ ...series, upcoming: false })}>去观看</button>
          : <ReserveButton series={series} reservations={reservations} />}
      </div>
    </div>
    {series.description && <p className="detail-description">{series.description}</p>}
    {!reservations.notificationsAllowed && reservations.items.some(row => row.seriesId === series.seriesId)
      && <button className="button" onClick={reservations.enable}><Bell size={17} />开启系统提醒</button>}
    {reservations.error && <p className="text-error" role="alert">{reservations.error}</p>}
  </section>;
}
