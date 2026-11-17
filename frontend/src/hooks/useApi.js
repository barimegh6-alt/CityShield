import { useCallback, useEffect, useRef, useState } from 'react';

export function useApi(fn, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const [tick, setTick] = useState(0);
  const fnRef = useRef(fn);
  fnRef.current = fn;
  useEffect(() => {
    let stale = false;
    setState((s) => ({ ...s, loading: true, error: null }));
    Promise.resolve()
      .then(() => fnRef.current())
      .then((data) => { if (!stale) setState({ data, loading: false, error: null }); })
      .catch((error) => { if (!stale) setState((s) => ({ data: s.data, loading: false, error })); });
    return () => { stale = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data: state.data, loading: state.loading, error: state.error, reload };
}

export function useAction(fn) {
  const [state, setState] = useState({ loading: false, error: null, data: null });
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const run = useCallback(async (...args) => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const data = await fnRef.current(...args);
      setState({ loading: false, error: null, data });
      return data;
    } catch (error) {
      setState((s) => ({ ...s, loading: false, error }));
      return undefined;
    }
  }, []);
  const reset = useCallback(() => setState({ loading: false, error: null, data: null }), []);
  return { run, loading: state.loading, error: state.error, data: state.data, reset };
}
