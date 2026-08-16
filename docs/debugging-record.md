# デバッグ記録: アプリケーション生成 UUID が新規集約を既存扱いにする問題

## 目的

Java 21、Spring Data JDBC、および H2 を使い、アプリケーションが事前に設定した UUID により新規 `WorkItem` が既存扱いとなる不具合を、実行可能な最小例で確認します。

> 契約: UUID を持つ新規 `WorkItem` を `WorkItemRepository.save` に渡したとき、1 件の行が保存され、同じ UUID で再読込できること。バグ状態では、存在しない ID への UPDATE による `IncorrectUpdateSemanticsDataAccessException` になります。

## 実行環境と再現境界

| 項目 | 内容 |
| --- | --- |
| 言語処理系 | OpenJDK 21.0.11 |
| 難易度プロファイル | 実践・上級。アプリケーション生成 ID と Spring Data JDBC の new 判定が相互作用する境界条件を扱います。 |
| ビルド・テスト方法 | Maven 3.8.7 で `mvn test` を実行します。 |
| 使用する依存関係 | Spring Boot 3.4.4、Spring Data JDBC、H2、JUnit Jupiter、AssertJ。 |
| 使用しないもの | 外部 DB、Web サーバー、コンテナ、外部 API、本番データ。 |
| 公開境界 | `WorkItemRepository.save(WorkItem)` と `findById(UUID)`。 |
| 最終観測 | `findById` によるタイトルの独立再読込と、`count()` による行数確認。 |
| 決定性の確保 | H2 のインメモリ DB とテストメソッドごとの DDL 初期化を使います。UUID の具体的な値には依存しません。 |

Spring Data JDBC 固有の状態判定を対象にするため、フレームワーク非依存の Java 言語仕様ラボではありません。HTTP 応答やログだけで結論を出さず、Repository からの再読込を最終観測に選びました。

## 最初に観測した事実

| 観測順 | 事実 | 得られた証拠 |
| --- | --- | --- |
| 1 | `WorkItem.create` は `@Id` にランダムな UUID を設定し、空の `"work_item"` テーブルに保存します。 | `WorkItemRepositoryTest` の Arrange と `schema.sql`。 |
| 2 | バグ状態で `repository.save` は正常終了せず、`IncorrectUpdateSemanticsDataAccessException` を送出しました。 | `mvn -Dtest=WorkItemRepositoryTest test` の終了コード 1。1 テスト中 1 失敗、0 エラー。 |
| 3 | 例外は「ID がデータベースに存在しない」と報告しました。 | テスト出力の `Failed to update entity ... Id [...] not found in database`。 |
| 4 | 修正後は、同じ Repository 境界から `findById` でタイトルを再読込でき、`count()` は 1 を返しました。 | 修正後の 2 契約テストが成功しました。 |

バグ状態のコミットは [`35ead17`](https://github.com/tonbiattack/spring-data-jdbc-newness-debug-lab/commit/35ead17) です。このコミットで `mvn -Dtest=WorkItemRepositoryTest test` を実行すると、設定やコンパイルではなく、意図した 0 行更新例外により失敗します。作成時に H2 の未引用テーブル名との大小文字不一致を一度観測しましたが、これは対象不具合ではないため、テーブル・列名を Spring Data JDBC の引用付き SQL に合わせてからバグコミットを作成しました。

## 競合仮説と検証

| 仮説 | 予測 | 検証 | 結果 |
| --- | --- | --- | --- |
| 事前設定された UUID により既存集約と判定される。 | 新規行がなくても UPDATE が選択され、0 行更新例外になります。 | `@Version` を `null` にした同じ集約・同じ Repository 境界で再実行します。 | 支持。初回保存が成功し、再読込できました。 |
| H2 の UUID 型またはスキーマが UUID を保存できない。 | version 列を加えても同じスキーマで保存が失敗します。 | 修正後、同じ H2・UUID 主キー・引用付き識別子で INSERT、再読込、UPDATE を行います。 | 除外。3 操作すべてが成功しました。 |
| テストのトランザクション境界が再読込を妨げている。 | 状態判定を変えても `findById` が失敗します。 | 同じ `@DataJdbcTest` と同じ DDL で、修正後に `findById` と `count()` を確認します。 | 除外。タイトルを再読込でき、件数は 1 でした。 |

## 確定した原因

この再現で直接観測した事実は、非 `null` UUID を持つ新規集約の `save` が 0 行更新例外で失敗したことです。Spring の公式解説では、Spring Data JDBC の既定戦略は ID が `null` でない集約を既存とみなし UPDATE を実行し、実際には新規であれば 0 行更新の例外になると説明されています。[2] したがって、このラボでは、アプリケーションが UUID を生成する設計と ID ベースの既定 new 判定との不一致が原因です。

公式の永続化ドキュメントは、保存後のエンティティが new のままであってはならず、new かどうかがエンティティ状態の一部であると説明しています。[1] この一般則と上の最小実験を組み合わせ、UUID 自体や H2 の型ではなく、集約の状態判定を修正対象に確定しました。

## 最小修正

`WorkItem` に `@Version Long version` を追加し、スキーマに `"VERSION" BIGINT` を追加しました。新規集約の version は `null` であり、Spring Data JDBC は version 属性を new 判定にも使用するため、ID がすでに設定されていても初回保存では INSERT を選択できます。[2] 再読込した集約には version が入り、次の保存は UPDATE として扱われます。

この修正は状態判定だけを対象にします。`Persistable` による独自状態管理、`JdbcAggregateTemplate.insert` への置換、ID 生成の DB 移管、Web API の追加、依存関係の追加は行いません。修正コミットは [`a1fecfb`](https://github.com/tonbiattack/spring-data-jdbc-newness-debug-lab/commit/a1fecfb) です。

## 回帰保証

| 守ること | テストまたは診断 | 修正後の結果 |
| --- | --- | --- |
| UUID を持つ新規集約を INSERT できること | `save_inserts_a_new_work_item_even_when_its_uuid_is_assigned_by_the_application` | 保存後にタイトルを再読込でき、件数は 1 です。 |
| 初回保存後の集約を UPDATE できること | `save_updates_an_aggregate_reloaded_after_its_initial_insert` | 再読込した集約のタイトルを更新でき、件数は 1 のままです。 |
| テスト全体が成功すること | `mvn test` | 2026-08-16 に 2 テスト成功、0 失敗、0 エラーを確認しました。 |

## 再現手順

```bash
# 修正済み状態を検証する
mvn test

# バグ状態を確認する。作業中の変更は先に退避する
git switch --detach 35ead17
mvn -Dtest=WorkItemRepositoryTest test

# 修正済み状態へ戻る
git switch main
```

## スコープと注意点

このラボは、Spring Boot 3.4.4 と H2 を使う Spring Data JDBC の Repository で、アプリケーション生成 UUID を持つ単一集約を保存する条件に限って再現・修正を確認しました。ほかの Spring Data モジュール、別の RDBMS、複雑な子エンティティを持つ集約、性能、競合時の処理へ同じ結論を自動的に拡張しません。`@Version` を導入する場合は、対象システムの楽観ロック要件とスキーマ移行方針を別途検討してください。

## References

[1]: https://docs.spring.io/spring-data/relational/reference/jdbc/entity-persistence.html "Persisting Entities :: Spring Data Relational"
[2]: https://spring.io/blog/2021/09/09/spring-data-jdbc-how-to-use-custom-id-generation "Spring Data JDBC - How to use custom ID generation"
