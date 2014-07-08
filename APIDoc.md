## API
# manager
## add_task
タスクを追加する
### request
```json
{
  "message_type": "add_task",
  "parameters": {
    "from": "XXXX",  // クライアント名 必須
    "id": "XXXXXXX", // クライアントが指定するそれぞれのID クライアント毎にユニーク 必須
    "group": "XXXX", // グループ分け用文字列 任意
    "type": "MP-API", // タスク種別 必須
    "task": {  // リクエスト先の情報 必須
      "url": "https://selenium-rd.ssreg.shanon-test.jp/services/rest/visitor", // MP APIのURL
      "method": "PUT", // HTTP Method
      "body": "<?xml version=\"1.0\" encoding=\"UTF-8\">
<VisitorData xmlns=\"http://smartseminar.jp/\" version=\"1.5\">
  <Id>10</Id>
  <Attribute1>10</Attribute10>
</VisitorData>", // MPに送るAPIの内容
    },
    "return_path": "http://url.to/push/notify/when/finish" // 指定するとこのURLに結果がPOSTされる。空ならsenderに返す
  }
}

```
### resonse
```json
{
  "message_type": "add_task_response",
  "count": {
    "from": 0,
    "to": 1,
    "total": 1
  },
  "results": [ {
    "from": "XXXX",   // リクエスト時に指定されたクライアント名
    "id": "XXXX",     // リクエスト時に指定されたID
    "status": "ack",  // 結果 ack reject
    "codd": 123,      // レスポンスコード
    "message": "XXXX" // rejectされた際のエラーメッセージ
  } ]
}
```

## get_status
追加したタスクの現在の状況を取得する
### request
```json
{
  "message_type": "get_status",
  "parameters": {
    "from": "XXXX",      // クライアント名 必須
    "id": "XXXX",        // クライアントが指定したID
    "group": "XXXX",     // クライアントが指定したグループ id か groupが必須
    "only_count": false, // 件数だけ取得するためのフラグ
    "offset": N,         // ページング用 offset/limit
    "limit": NN
  }
}
```

### response
```json
{
  "message_type": "get_status_response",
  "count": {
    "from": 0,
    "to": 2,
    "total": 2
  },
  "results": [
    {
      "from": "XXXX",        // タスク登録時に指定されたクライアント名
      "id": "XXXX",          // タスク登録時に指定されたID
      "group": "XXXX",       // タスク登録時に指定されたグループ名
      "status": "finish",    // 現在のステータス wait/running/finish/error
      "task_type": "MP-API", // タスクの種類
      "result": {            // タスク実行結果 完了していなければnull
        "code": 200,
        "body": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Response xmlns=\"http://smartseminar.jp/\" version=\"1.26\"><AAA>aaa</AAA></Response>"
      }
    },
    {
      "from": "XXXX",
      "id": "XXXX",
      "group": "XXXX",
      "status": "wait",
      "type": "MP-API",
      "result": null
    }
  ]
}
```

## cancel_task
追加したタスクをキャンセルする
### request
```json
{
  "message_type": "cancel_task",
  "parameters": {
    "from": "XXXX",  // クライアント名 必須
    "id": "XXXX",    // タスク登録時に指定したID
    "group": "XXXX", // タスク登録時に指定したグループ名 IDかグループ名が必須
    "force": false,  // 実行中のpものも強制的にキャンセルするかどうか 
    "offset": N,     // ページング用 offset/limit
    "limit": NN
  }
}
```

### response
```json
{
  "message_type": "cancel_task_response",
  "count": {
    "from": 0,
    "to": 2,
    "total": 2
  },
  "results": [
    {
      "from": "XXXX",        // タスク登録時に指定されたクライアント名
      "id": "XXXX",          // タスク登録時に指定されたID
      "group": "XXXX",       // タスク登録時に指定されたグループ名
      "task_type": "MP-API", // タスクの種類
      "status": "finish",    // キャンセル実行の結果
      "code": 200,      // キャンセル結果のコード 2XX 3XX 4XX 5XX HTTPのコードに準ずる 
      "message": "XXXX" // エラー時のみ
    },
    {
      "from": "XXXX",        // タスク登録時に指定されたクライアント名
      "id": "XXXX",          // タスク登録時に指定されたID
      "group": "XXXX",       // タスク登録時に指定されたグループ名
      "task_type": "MP-API", // タスクの種類
      "status": "finish",    // キャンセル実行の結果
      "code": 200,      // キャンセル結果のコード 2XX 3XX 4XX 5XX HTTPのコードに準ずる 
      "message": "XXXX" // エラー時のみ
    }
  ]
}
```
