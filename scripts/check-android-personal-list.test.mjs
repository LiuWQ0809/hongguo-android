import assert from 'node:assert/strict';
import test from 'node:test';
import { libraryGenre, matchesPersonalTitle } from '../src/android/personal-list.mjs';

test('剧种只使用明确标签，冲突或缺失时保留未分类', () => {
  assert.equal(libraryGenre({ tags: ['都市', '真人短剧'] }), '真人剧');
  assert.equal(libraryGenre({ tags: ['ＡＩ剧'] }), 'AI剧');
  assert.equal(libraryGenre({ tags: ['漫画短剧', '动态漫'] }), '漫剧');
  assert.equal(libraryGenre({ title: 'AI剧里的我', tags: ['都市'] }), '未分类');
  assert.equal(libraryGenre({ tags: ['AI剧', '真人剧'] }), '未分类');
  assert.equal(libraryGenre({ tags: '真人剧' }), '未分类');
});

test('片单和下载按规范化剧名筛选', () => {
  assert.equal(matchesPersonalTitle('冒险ＡＩ 2', '　ai 2　'), true);
  assert.equal(matchesPersonalTitle('冒险ＡＩ 2', '冒险'), true);
  assert.equal(matchesPersonalTitle('冒险ＡＩ 2', '第一季'), false);
  assert.equal(matchesPersonalTitle(undefined, ''), true);
});
