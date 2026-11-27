import React, { useEffect, useRef, useState } from 'react';
import ReleaseBrowser from './ReleaseBrowser.jsx';
import { useReleaseCalendar } from './use-release-calendar.mjs';

const monthDay = date => `${Number(date.slice(4, 6))}月${Number(date.slice(6))}日`;

export default function CalendarBrowser(props) {
  const { Status, PosterGrid, onOpen, onPlay } = props;
  const [recent, setRecent] = useState(false);
  const calendar = useReleaseCalendar();
  const selectedButton = useRef(null);
  useEffect(() => {
    selectedButton.current?.scrollIntoView({ block: 'nearest', inline: 'center' });
  }, [calendar.selected, recent]);
  return <>
    <div className="home-section-head">
      <h2>{recent ? '近期上新' : '上新日历'}</h2>
      <button className="button" onClick={() => setRecent(value => !value)}>{recent ? '返回日历' : '近七天列表'}</button>
    </div>
    {recent ? <ReleaseBrowser {...props} /> : <>
      {!!calendar.dates.length && <div className="calendar-days" role="group" aria-label="上新日期">
        {calendar.dates.map(date => <button className="button" type="button" key={date}
          ref={date === calendar.selected ? selectedButton : null} disabled={calendar.busy}
          aria-pressed={date === calendar.selected} onClick={() => calendar.choose(date)}>
          {date === calendar.today ? <><strong>今天</strong><span>{monthDay(date)}</span></> : monthDay(date)}
        </button>)}
      </div>}
      <Status busy={calendar.busy && !calendar.items.length} error={calendar.error} preserve={!!calendar.items.length}
        onRetry={calendar.retry} empty={!calendar.items.length && { title: '这一日暂无排期', text: '可以查看其他日期或继续加载。' }}>
        <PosterGrid items={calendar.items} onOpen={onOpen} onPlay={onPlay} />
      </Status>
      {calendar.hasMore && !calendar.error && <div className="home-more">
        <button type="button" className="button" disabled={calendar.busy} onClick={calendar.more}>加载更多排期</button>
      </div>}
    </>}
  </>;
}
