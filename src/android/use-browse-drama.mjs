import { useCallback, useEffect, useRef, useState } from 'react';
import { nativeCall } from './bridge.mjs';
import { parseNewReleases } from './new-releases.mjs';

export function useBrowseDrama() {
  const [state, setState] = useState({ busy: true, items: [], groups: [], filters: {} });
  const generation = useRef(0);
  const load = useCallback(async (args = {}, append = false) => {
    const current = ++generation.current;
    setState(previous => ({ ...previous, busy: true, error: '', failed: null,
      items: append ? previous.items : [], filters: args.filters || {} }));
    try {
      const result = await nativeCall('browseDrama', args, 22000);
      const items = parseNewReleases(result?.items);
      if (generation.current !== current) return;
      setState(previous => {
        const base = append ? previous.items : [];
        const seen = new Set(base.map(row => row.seriesId));
        return { ...result, busy: false, filters: args.filters || {},
          groups: append ? previous.groups : result.groups,
          items: [...base, ...items.filter(row => !seen.has(row.seriesId))] };
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
    apply: filters => load({ filters }),
    retry: () => load(state.failed?.args, state.failed?.append),
    more: () => load({ filters: state.filters, offset: state.offset, sessionId: state.sessionId }, true),
  };
}
