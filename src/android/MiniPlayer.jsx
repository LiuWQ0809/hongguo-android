import React, { useEffect, useRef, useState } from 'react';
import { ChevronRight, Pause, Play, X } from '../components/icons.jsx';
import { nativeCall } from './bridge.mjs';

export function useMiniPlayer(enabled, notify) {
  const [session, setSession] = useState(null);
  const [busy, setBusy] = useState(false);
  const request = useRef(0), sending = useRef(false);
  useEffect(() => {
    if (!enabled) return;
    let disposed = false, fetching = false;
    const refresh = async () => {
      if (disposed || fetching || sending.current || document.hidden) return;
      const revision = ++request.current;
      fetching = true;
      try {
        const result = await nativeCall('getActivePlayback');
        if (!disposed && revision === request.current) setSession(result?.id ? result : null);
      } catch (_) { /* A subsequent visible-page refresh retries. */ }
      finally { fetching = false; }
    };
    refresh();
    const timer = setInterval(refresh, 1800);
    window.addEventListener('androidresume', refresh);
    document.addEventListener('visibilitychange', refresh);
    return () => { disposed = true; clearInterval(timer); window.removeEventListener('androidresume', refresh); document.removeEventListener('visibilitychange', refresh); };
  }, [enabled]);
  const command = async action => {
    if (!session?.id || sending.current) return;
    sending.current = true; request.current++; setBusy(true);
    try {
      const result = await nativeCall('controlActivePlayback', { id: session.id, action });
      if (action !== 'open') setSession(result?.id ? result : null);
    } catch (error) { notify(error.message || '播放操作未成功'); }
    finally { sending.current = false; setBusy(false); }
  };
  return { session: enabled ? session : null, command, busy };
}

export default function MiniPlayer({ session, command, busy }) {
  if (!session) return null;
  const caption = session.paused ? '已暂停' : session.ready ? '正在播放' : '正在加载';
  return <section className="mini-player" aria-label="当前播放">
    <button type="button" className="mini-title" onClick={() => command('open')} disabled={busy} aria-label={`打开播放器，${session.title}第 ${session.index} 集`}><strong>{session.title}</strong><small>第 {session.index} 集 · {caption}</small></button>
    <button type="button" aria-label="上一集" onClick={() => command('previous')} disabled={busy || !session.previous}><ChevronRight className="mini-previous" size={20} /></button>
    <button type="button" aria-label={session.paused ? '继续播放' : '暂停播放'} onClick={() => command(session.paused ? 'play' : 'pause')} disabled={busy}>{session.paused ? <Play size={20} /> : <Pause size={20} />}</button>
    <button type="button" aria-label="下一集" onClick={() => command('next')} disabled={busy || !session.next}><ChevronRight size={20} /></button>
    <button type="button" aria-label="停止播放" onClick={() => command('stop')} disabled={busy}><X size={18} /></button>
  </section>;
}
