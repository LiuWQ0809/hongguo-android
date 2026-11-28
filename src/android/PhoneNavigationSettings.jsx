import React from 'react';

const KEY = 'hongguo.phone-navigation.v1';
const LABELS = { discover: '首页', library: '追剧', reservations: '预约', downloads: '下载', about: '我的' };
const DEFAULT = ['discover', 'library', 'about'];
const locked = id => id === 'discover' || id === 'about';

export function readPhoneNavigation() {
  try {
    const value = JSON.parse(localStorage.getItem(KEY) || 'null');
    if (Array.isArray(value) && value.length <= 5 && value.includes('discover') && value.includes('about')
        && value.every(id => typeof id === 'string' && Object.hasOwnProperty.call(LABELS, id))
        && new Set(value).size === value.length) return value;
  } catch (_) { /* Stored preferences may predate this release. */ }
  return DEFAULT;
}

export function storePhoneNavigation(order) { localStorage.setItem(KEY, JSON.stringify(order)); }

export default function PhoneNavigationSettings({ order, change }) {
  const toggle = id => change(order.includes(id) ? order.filter(item => item !== id) : [...order.slice(0, -1), id, order.at(-1)]);
  const move = (index, delta) => {
    const next = order.slice();
    [next[index], next[index + delta]] = [next[index + delta], next[index]];
    change(next);
  };
  return <section className="about-section" id="settings-navigation" tabIndex={-1}>
    <h2>底栏导航</h2>
    {order.map((id, index) => <div className="playback-setting" key={id}>
      <span>{LABELS[id]}</span>
      <div className="about-links">
        <button type="button" className="button" aria-label={`左移${LABELS[id]}`} disabled={!index} onClick={() => move(index, -1)}>←</button>
        <button type="button" className="button" aria-label={`右移${LABELS[id]}`} disabled={index === order.length - 1} onClick={() => move(index, 1)}>→</button>
        {!locked(id) && <button type="button" className="button" aria-label={`隐藏${LABELS[id]}`} onClick={() => toggle(id)}>隐藏</button>}
      </div>
    </div>)}
    <div className="about-links">{Object.keys(LABELS).filter(id => !order.includes(id)).map(id =>
      <button type="button" key={id} className="button" onClick={() => toggle(id)}>添加{LABELS[id]}</button>)}
      <button type="button" className="button" onClick={() => change(DEFAULT)}>恢复默认</button>
    </div>
  </section>;
}
