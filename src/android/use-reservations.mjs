import { useCallback, useEffect, useRef, useState } from 'react';
import { nativeCall } from './bridge.mjs';

const EMPTY = { items: [], messages: [], notificationsAllowed: false };

export function useReservations(enabled) {
  const [state, setState] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const request = useRef(false);
  const live = useRef(false);
  const run = useCallback(async (method, params, timeout) => {
    if (request.current) return false;
    request.current = true;
    setBusy(true); setError('');
    try {
      const result = await nativeCall(method, params, timeout);
      if (!Array.isArray(result?.items) || !Array.isArray(result?.messages)) throw new Error('预约列表读取失败');
      if (live.current) {
        setState(result);
        if (result.complete === false) setError('部分提醒尚未核实，请稍后再检查。');
      }
      return true;
    } catch (cause) {
      if (live.current) setError(cause.message || '操作未完成，请重试。');
      return false;
    } finally { request.current = false; if (live.current) setBusy(false); }
  }, []);
  useEffect(() => {
    if (!enabled) return;
    live.current = true;
    const load = () => run('getReservations', {});
    load();
    window.addEventListener('androidresume', load);
    return () => { live.current = false; window.removeEventListener('androidresume', load); };
  }, [enabled, run]);
  return { ...state, busy, error,
    items: state.items.filter(row => !row.follow),
    follows: state.items.filter(row => row.follow === true),
    toggleFollow: (series, enabled, latestEpisode) => run('setReservation', {
      seriesId: series.seriesId, series, enabled, follow: true, latestEpisode,
    }),
    toggle: (series, enabled) => run('setReservation', { seriesId: series.seriesId, series, enabled }),
    load: () => run('getReservations', {}),
    readMessage: messageId => run('readReservationMessage', { messageId }),
    refresh: () => run('checkReservations', {}, 50000),
    enable: () => nativeCall('enableReservationNotifications').catch(cause => setError(cause.message)),
  };
}
