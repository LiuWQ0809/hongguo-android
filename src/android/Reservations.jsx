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
  const [online, setOnline] = useState(false);
  const items = reservations.items.filter(row => Boolean(row.online) === online);
  return <section className="reservations-page">
    <button className="back-button" onClick={onBack}><ChevronRight size={20} />返回追剧</button>
    <header className="page-heading heading-actions">
      <h1>我的预约</h1>
      <button type="button" className="button" disabled={reservations.busy || !reservations.items.length}
        onClick={reservations.refresh}>{reservations.busy ? '检查中…' : '检查上线'}</button>
    </header>
    <div className="section-tabs" role="group" aria-label="预约状态">
      <button aria-pressed={!online} onClick={() => setOnline(false)}>待上线</button>
      <button aria-pressed={online} onClick={() => setOnline(true)}>已上线</button>
    </div>
    {!reservations.notificationsAllowed && <button className="button" type="button" onClick={reservations.enable}>
      <Bell size={17} />开启系统提醒
    </button>}
    {reservations.error && <p className="text-error" role="alert">{reservations.error}</p>}
    {!items.length && <p className="result-caption">{online ? '暂无已上线的预约' : '在预约榜选择想看的短剧，预约会保存在本机。'}</p>}
    <div className="reservation-list">{items.map(row => <article key={row.seriesId} className="reservation-row">
      <button className="reservation-title" type="button" onClick={() => online ? onPlay(row) : onOpen({ ...row, upcoming: true })}>
        <strong>{row.title}</strong><span>{online ? '已上线 · 去观看' : row.expected || '待上线'}</span>
      </button>
      <ReserveButton series={row} reservations={reservations} />
    </article>)}</div>
  </section>;
}
