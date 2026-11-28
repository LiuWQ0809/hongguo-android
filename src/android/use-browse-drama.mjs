import { useCallback, useEffect, useRef, useState } from 'react';
import { nativeCall } from './bridge.mjs';
import { parseNewReleases } from './new-releases.mjs';

export function useAndroidDirectory(method, parseItems, itemKey) {
  const [state, setState] = useState({ busy: true, items: [], groups: [], filters: {} });
  const generation = useRef(0);
  const load = useCallback(async (args = {}, append = false) => {
    const current = ++generation.current;
    setState(previous => ({ ...previous, busy: true, error: '', failed: null,
      items: append ? previous.items : [], filters: args.filters || {} }));
    try {
      const result = await nativeCall(method, args, 22000);
      const items = parseItems(result?.items);
      if (generation.current !== current) return;
      setState(previous => {
        const base = append ? previous.items : [];
        const seen = new Set(base.map(itemKey));
        return { ...result, busy: false, filters: args.filters || {},
          groups: append ? previous.groups : result.groups,
          items: [...base, ...items.filter(row => !seen.has(itemKey(row)))] };
      });
    } catch (cause) {
      if (generation.current === current)
        setState(previous => ({ ...previous, busy: false, error: cause.message, failed: { args, append } }));
    }
  }, [method, parseItems, itemKey]);
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

const seriesKey = row => row.seriesId;
export function useBrowseDrama() {
  return useAndroidDirectory('browseDrama', parseNewReleases, seriesKey);
}
