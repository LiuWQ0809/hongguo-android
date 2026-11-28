import { readFileSync, readdirSync } from 'node:fs';
import assert from 'node:assert/strict';
import { test } from 'node:test';

const ui = new URL('../src/android/', import.meta.url);
const bridge = readFileSync(new URL('bridge.mjs', ui), 'utf8');
const native = readFileSync(new URL('../android/app/src/main/java/io/github/jackotom/hongguo/android/NativeBridge.java', import.meta.url), 'utf8');
const methodNames = block => new Set([...block.matchAll(/["']([A-Za-z][A-Za-z0-9]*)["']/g)].map(match => match[1]));
const uiMethods = methodNames(bridge.match(/const METHODS = new Set\(\[([\s\S]*?)\]\)/)[1]);
const nativeMethods = methodNames(native.match(/private static final Set<String> METHODS = [^;]+;/)[0]);

test('visible playback, history and reservation controls are accepted on both sides', () => {
  const expected = ['getActivePlayback', 'controlActivePlayback', 'getPlaybackCuts', 'setPlaybackCuts', 'removeLibraryRows', 'readReservationMessage'];
  for (const name of expected) {
    assert.ok(uiMethods.has(name), `UI bridge rejects ${name}`);
    assert.ok(nativeMethods.has(name), `native bridge rejects ${name}`);
  }
});

test('literal Android UI calls are exposed by the page bridge', () => {
  for (const name of readdirSync(ui).filter(name => /\.(jsx|mjs)$/.test(name))) {
    const source = readFileSync(new URL(name, ui), 'utf8');
    for (const call of source.matchAll(/nativeCall\('([A-Za-z][A-Za-z0-9]*)'/g)) {
      assert.ok(uiMethods.has(call[1]), `${name} calls blocked method ${call[1]}`);
    }
  }
});
