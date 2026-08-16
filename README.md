# Spring Data JDBC: アプリケーション生成 UUID が新規集約を既存扱いにするデバッグラボ

Spring Data JDBC で UUID をアプリケーション側から設定した集約を保存するとき、既定の状態判定により **新規の集約が UPDATE 対象になる**不具合を、失敗する契約テストから修正と回帰確認まで追跡する Java 21 向けの実行可能なラボです。

## この題材で守る契約

> アプリケーションが UUID を設定して作成した新規 `WorkItem` を `CrudRepository.save` に渡したとき、1 件の行が INSERT され、同じ UUID で独立に再読込できることを守ります。

バグ状態では、`@Id` が非 `null` であるために `WorkItem` が既存として扱われ、存在しない行への更新により `IncorrectUpdateSemanticsDataAccessException` が発生します。Spring Data JDBC は、既定では ID が `null` でない集約を既存とみなし、保存後の集約が new のままではならないと説明しています。[1] [2]

## 最短の開始手順

修正済みの既定ブランチで、次を実行します。

```bash
mvn test
```

2026-08-16 にこのコマンドを実行し、2 件のテストがすべて成功することを確認しました。

## バグを再現する

バグ状態はコミット [`35ead17`](https://github.com/tonbiattack/spring-data-jdbc-newness-debug-lab/commit/35ead17) に保存しています。作業中の変更を退避してから、次を実行してください。

```bash
git switch --detach 35ead17
mvn -Dtest=WorkItemRepositoryTest test
```

テストは 1 件失敗し、`IncorrectUpdateSemanticsDataAccessException` により「ID がデータベースに存在しない」と報告されます。確認後は修正済みの既定ブランチへ戻ります。

```bash
git switch main
```

## 観測の要約

| 観測点 | バグ状態 | 修正後 |
| --- | --- | --- |
| 直接結果 | 新規 `WorkItem` の保存が 0 行更新例外で失敗します。 | 新規 `WorkItem` の保存が成功します。 |
| 最終状態 | 保存処理が例外で停止するため、再読込できる行はありません。 | `findById` でタイトルを再読込でき、件数は 1 件です。 |
| 更新の回帰 | 初回 INSERT まで到達しません。 | 再読込した集約を保存すると同じ 1 行が更新されます。 |
| 検証コマンド | `mvn -Dtest=WorkItemRepositoryTest test` は失敗します。 | `mvn test` は 2 テスト成功します。 |

詳細な仮説、証拠、原因、修正、回帰保証は [`docs/debugging-record.md`](docs/debugging-record.md) に記録しています。

## 構成

```text
src/main/java/                         Spring Boot アプリケーションと集約・Repository
src/test/java/                         保存と再読込を検証する契約テスト
src/test/resources/schema.sql          H2 用の決定的なスキーマ
README.md                              学習の開始手順
docs/topic-brief.md                    題材の範囲と仮説
docs/debugging-record.md               証拠に基づく調査記録
```

## 前提条件

| 項目 | バージョンまたは条件 |
| --- | --- |
| JDK | Java 21 |
| ビルド・テストツール | Maven 3.8 以降 |
| 主な依存関係 | Spring Boot 3.4.4、Spring Data JDBC、H2、JUnit Jupiter |
| 外部サービス | 不要。テストはインメモリ H2 を使用します。 |

## スコープ

このラボは、Spring Data JDBC の Repository に UUID が設定済みの**新規集約**を保存する一つの契約だけを扱います。UUID の衝突、DB による ID 生成、Web API、複数集約、分離レベル、および楽観ロック競合の一般的な設計方針を示すものではありません。`@Version` はこの再現で new 判定と更新時のバージョン管理を満たすために採用しており、すべてのドメインモデルに必須という主張ではありません。

## References

[1]: https://docs.spring.io/spring-data/relational/reference/jdbc/entity-persistence.html "Persisting Entities :: Spring Data Relational"
[2]: https://spring.io/blog/2021/09/09/spring-data-jdbc-how-to-use-custom-id-generation "Spring Data JDBC - How to use custom ID generation"
