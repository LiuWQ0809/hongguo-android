import { useCallback, useEffect, useRef, useState } from 'react';
import { nativeCall } from './bridge.mjs';

export function useReleaseCalendar() {
  const [state, setState] = useState({ busy: true, dates: [], items: [] });
  const generation = useRef(0);
  const load = useCallback(async (args = {}, append = false) => {
    const current = ++generation.current;
    setState(previous => ({ ...previous, busy: true, error: '', failed: null,
      items: append ? previous.items : [] }));
    try {
      const result = await nativeCall('releaseCalendar', args, 24000);
      if (generation.current !== current) return;
      if (!Array.isArray(result?.dates) || !Array.isArray(result.items))
        throw new Error('上新日历数据无效');
      setState(previous => {
        const seen = new Set(append ? previous.items.map(row => row.seriesId) : []);
        return { ...result, busy: false, items: [
          ...(append ? previous.items : []),
          ...result.items.filter(row => !seen.has(row.seriesId)),
        ] };
      });
    } catch (cause) {
      if (generation.current === current)
        setState(previous => ({ ...previous, busy: false, error: cause.message, failed: { args, append } }));
    }
  }, []);
  useEffect(() => {
    load();
    return () => { generation.current++; };
  }, [load]);
  return { ...state,
    choose: date => load({ date }),
    retry: () => load(state.failed?.args, state.failed?.append),
    more: () => load({ date: state.selected, offset: state.offset, subscribeOffset: state.subscribeOffset,
      sessionId: state.sessionId }, true),
  };
}
