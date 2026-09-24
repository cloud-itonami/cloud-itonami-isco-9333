# physai-isco-9333 — 貨物の積み降ろし（パレット荷役） の physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isco-9333`、ISCO 9333 貨物の積み降ろし作業員）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: パレット荷役ロボットが、貨物の積み込み・積み降ろし・重量確認を行う（荷役ドック・フォークリフト・過積載の近くでの作業は人の承認が要る）。物理的な仕事は、積んだパレットをドックレベラーを越えてトレーラーへ運び込むことと、積み上げたパレットを倉庫内で運ぶこと（非常停止で倒れないのは何 m まで積んだときか）。
その物理的な仕事を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:pallet-over-dock-leveller` | transport | 1000 kg のパレットをドックからドックレベラーを越えてトレーラーへ 8 m 運ぶ（荷台の高さで勾配が変わる） | 1 区間の所要時間 | 20 s（estimate） |
| `:stacked-pallet-e-stop` | transport | 800 kg の積み上げパレットを倉庫内 30 m 運び、2 m/s² で非常停止（荷の重心高さを変える） | 最小転倒余裕 | 0.3 以上（estimate） |

測定の入口: `kbb -M:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:physai-test`（`test-physai/freighthandling/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する。
この alias は repo 自身の `test/` の `.cljk` も kbb の runner で一緒に走らせる）。

## 測って分かったこと・限界（成長の第一候補）

1. **ドックレベラー**: 0〜2° では 10.29 s（加速度上限 0.3 m/s²）、4° で駆動力が効いて 10.35 s、5° で 12.83 s、6° 以上で **停止**。境界は **約 5.45°** —— 駆動力 1500 N では約 9.5% を越えるレベラー勾配を 1 t の荷で上れない。
   エネルギーは 0° で 1715 J、5° で 10547 J。
2. **積み上げパレット**: 所要時間 22.87 s は荷の高さに依らない。転倒余裕は荷の重心 0.6 m で 0.699、1.4 m で 0.388、1.8 m で 0.233、2.2 m で 0.077。
   限界 0.3 を割るのは荷の重心 **約 1.63 m**。
3. **estimate のままの値**（成長候補）: 区間所要時間 20 s（積み込みの作業基準）、転倒余裕 0.3（パレットロボットの安定性資料・積載基準で置き換える）、
   駆動力 1500 N・転がり抵抗係数 0.01・非常停止の減速度 2 m/s²（ロボットの仕様書）。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この職種のロボットがする別の物理的な仕事を 1 case 足す（例: 重量確認の秤への載せ降ろし、ストレッチフィルムの引張、冷凍貨物の温度上昇）。
   `:kind` は :transport / :manipulator / :material / :thermal / :tank-drain / :pipe-flow。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isco-9333 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:physai-test → kbb -M:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isco-9333 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
