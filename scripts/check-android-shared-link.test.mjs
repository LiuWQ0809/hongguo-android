import assert from 'node:assert/strict';
import test from 'node:test';
import { parseSharedLink } from '../src/android/shared-link.mjs';

test('official share text preserves exact long IDs and supports legacy detail links', () => {
  const seriesId = '7696083096164125721', vid = '7696085244503411774';
  assert.deepEqual(parseSharedLink(`看看这部剧 https://hongguoduanju.com/player/${seriesId}/${vid}。打开红果`), { seriesId, vid });
  assert.deepEqual(parseSharedLink(`https://www.hongguoduanju.com/detail?series_id=${seriesId}`), { seriesId, vid: null });
  assert.deepEqual(parseSharedLink(seriesId, { allowId: true }), { seriesId, vid: null });
});

test('rejects conflicting targets, hostile origins and invalid episode IDs', () => {
  for (const text of ['https://hongguoduanju.com/player/1/2?vid=3', 'https://hongguoduanju.com/player/1/0',
    'https://hongguoduanju.com/player/1/x?series_id=1', 'https://hongguoduanju.com/player/1/2?series_id=3',
    'https://hongguoduanju.com.evil.example/player/1/2', 'https://me@hongguoduanju.com/player/1/2',
    'https://hongguoduanju.com/player/1/2 https://hongguoduanju.com/player/1/3', '1']) {
    assert.throws(() => parseSharedLink(text), text);
  }
});
