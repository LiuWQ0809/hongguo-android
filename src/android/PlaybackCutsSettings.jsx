import React, { useEffect, useState } from 'react';
import { nativeCall } from './bridge.mjs';

export default function PlaybackCutsSettings({ notify }) {
  const [values, setValues] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const load = () => nativeCall('getPlaybackCuts').then(setValues, error => setError(error.message));
  useEffect(() => { load(); }, []);
  const save = async event => {
    event.preventDefault();
    setBusy(true); setError('');
    try {
      const result = await nativeCall('setPlaybackCuts', { intro: Number(values.intro), outro: Number(values.outro) });
      setValues(result);
      notify('片头片尾设置已保存');
    } catch (error) { setError(error.message); }
    finally { setBusy(false); }
  };
  return <section className="about-section playback-settings" id="settings-cuts" aria-label="片头片尾" tabIndex={-1}>
    <h2>跳过片头片尾</h2>
    {values && <form onSubmit={save}>
      {Object.entries({ intro: '片头秒数', outro: '片尾秒数' }).map(([key, label]) => <div className="playback-setting" key={key}>
        <label htmlFor={`cut-${key}`}>{label}</label>
        <input id={`cut-${key}`} type="number" min="0" max="180" step="1" required
          value={values[key]} onChange={event => setValues({ ...values, [key]: event.target.value })} disabled={busy} />
      </div>)}
      <p className="account-note">填 0 可关闭，从新打开的分集开始生效。</p>
      <button className="button" type="submit" disabled={busy}>{busy ? '保存中…' : '保存'}</button>
    </form>}
    {error && <p className="account-error" role="alert">{error}{!values && <button type="button" className="button" onClick={load}>重试</button>}</p>}
  </section>;
}
