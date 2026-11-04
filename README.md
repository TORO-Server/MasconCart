トロッコに乗車して、Dキーを押すとマスコンモードのON/OFFができます。
Wキーでノッチアップ(加速)、Sキーでノッチダウン(ブレーキ)、AキーでNニュートラルを押して操作します。
最大P7、B1〜B7、EBを投入できます。
マスコンOFF中はWキーで通常発進できます。
停車中はスペースキーで進行方向を切り替えられます（押しっぱなしでは連続切替しません）。
マスコンON中はTrainCartsの看板制御が無視されるようになります。アナウンスなどは継続されます。

Click-to-MinecartのトロッコはマスコンOFF中は普通のトロッコになります。TrainCartsに変換されていない普通のトロッコは、マスコンをONにすると変換され、OFFにすると元に戻ります。
TrainCartsの列車は、乗っているだけでは変わりません。マスコンをOFFにすると、元の設定でTrainCartsの制御に戻ります。

## 権限と設定

- `masconcart.use`: `/mascon` とDキーでの切り替えを許可します。既定は全員です。
- `takeover.requireTag` (`config.yml`): `true` にすると、`takeover.tag` のタグが付いたTrainCartsの列車だけ操作できます。既定は `false` で、乗っている列車をどれでも操作できます。Click-to-Minecartのトロッコと、TrainCartsに変換されていない普通のトロッコは、この設定に関係なく操作できます。TrainCartsの `allMinecartsAreTrainCarts` が `true` のとき、アイテムで置いたトロッコはTrainCartsのトロッコなので、タグが必要です。
- `takeover.tag` (`config.yml`): 操作できる列車に付けるTrainCartsのタグです。既定は `mascon` で、`/train tags add mascon` で付けます。

Minecraft 26.2以降向け
