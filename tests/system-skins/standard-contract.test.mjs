import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root=new URL('../../../',import.meta.url);
const read=path=>fs.readFileSync(new URL(path,root));
const json=path=>JSON.parse(read(path));
const main='PAOPAOIME/apps/android/app/src/main/assets/system_themes/';
const oem='OEM/android/ai-ime/host-bridge/src/main/assets/system_themes/';
for(const skin of ['daylight_porcelain','night_graphite']){
 test(`${skin}: OEM ships the accepted template and 52 portrait/landscape popup contours`,()=>{
  const theme=json(oem+skin+'/theme.json');
  assert.equal(theme.revision,9);assert.equal(theme.renderStyle.skinLabStandard,true);
  assert.deepEqual(theme,json(main+skin+'/theme.json'));
  for(const asset of ['normal.png','function.png','enter.png','popup-geometry.json'])assert.deepEqual(read(oem+skin+'/'+asset),read(main+skin+'/'+asset));
  assert.equal(Object.keys(json(oem+skin+'/popup-geometry.json')).length,52);
  assert.equal(theme.genericActiveForegroundColor,-1);
  assert.equal(theme.renderStyle.layout.keyboardHeightDp,240);
  assert.equal(theme.renderStyle.layout.keyboardHeightLandscapeDp,160);
 });
}
test('six different keyboard type vectors match the main project in both OEM resource sets',()=>{
 const types=['pinyin9','pinyin26','english26','shuangpin','wubi','wubi_pinyin'];
 for(const dir of ['app','ai-ime/app']){
  const assets=types.map(type=>read(`OEM/android/${dir}/src/main/res/drawable/ic_oem_layout_${type}.xml`).toString());
  assert.equal(new Set(assets).size,6);
  types.forEach((type,i)=>assert.equal(assets[i],read(`PAOPAOIME/apps/android/${dir}/src/main/res/drawable/ic_oem_layout_${type}.xml`).toString()));
 }
});
