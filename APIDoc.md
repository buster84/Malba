
## API
# manager
## add
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
### req
```json
{
　"queue_id": 1234, // malba でのqueue id
  "id": "XXXX"      // クライアントが指定したID
}
```

### res
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
### req
```json
{
　"queue_id": 1234, // malba でのqueue id
  "id": "XXXX"      // クライアントが指定したID
}
```

### res
