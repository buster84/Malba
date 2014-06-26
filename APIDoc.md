## API
# manager
## add
タスクを追加する
### request
```json
{
  "id": "XXXXXXX", // クライアントが指定するそれぞれのID 任意 必須
  "target": {  // リクエスト先の情報 必須
    "url": "https://selenium-rd.ssreg.shanon-test.jp/services/rest/visitor", // MP APIのURL
    "method": "PUT", // HTTP Method
    "body": "<?xml version=\"1.0\" encoding=\"UTF-8\">
<VisitorData xmlns=\"http://smartseminar.jp/\" version=\"1.5\">
  <Id>10</Id>
  <Attribute1>10</Attribute10>
</VisitorData>", // MPに送るAPIの内容
  "return_path": "http://url.to/push/notify/when/finish" // 指定するとこのURLに結果がPOSTされる。空ならsenderに返す
}

```
### resonse
```json
{
  "queue_id": 1231231, // malba でのqueue id
  "wait_count": 12313  // 現在の待ち数
}
```

## status
追加したタスクの現在の状況を取得する
### request
```json
{
  "queue_id": 1234, // malba でのqueue id
  "id": "XXXX"      // クライアントが指定したID
}
```

### response
```json
{
  "queue_id": 1234, // malba でのqueue_id
  "id": "XXXXX",    // クライアントが指定したID
  "status": "wait", // 現在のステータス wait running finish error
  "response": {     // MPから帰ってきたレスポンス
    "code": 200,    // レスポンスコード
    "body": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Response xmlns=\"http://smartseminar.jp/\" version=\"1.26\"><AAA>aaa</AAA></Response>"
  }
}
```

## cancel
追加したタスクをキャンセルする
### request
```json
{
  "queue_id": 1234, // malba でのqueue id
  "id": "XXXX"      // クライアントが指定したID
}
```

### response
```json
{
  "code": 200,      // キャンセル結果のコード 2XX 3XX 4XX 5XX HTTPのコードに準ずる
  "message": "XXXX" // エラー時のみ
}
```
