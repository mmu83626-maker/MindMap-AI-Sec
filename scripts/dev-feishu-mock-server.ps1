$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$envPath = Join-Path $root ".env"
$eventLog = Join-Path $root "feishu-events.log"

function Import-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        return
    }

    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            return
        }

        $name, $value = $line.Split("=", 2)
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), "Process")
    }
}

function Write-EventLog {
    param([string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -Path $eventLog -Value "[$timestamp] $Message" -Encoding UTF8
}

function ConvertTo-JsonCompact {
    param($Value)
    return ($Value | ConvertTo-Json -Depth 20 -Compress)
}

function Get-FeishuTenantToken {
    $appId = $env:FEISHU_APP_ID
    $appSecret = $env:FEISHU_APP_SECRET
    if (-not $appId -or -not $appSecret) {
        throw "Missing FEISHU_APP_ID or FEISHU_APP_SECRET in .env"
    }

    $body = ConvertTo-JsonCompact @{ app_id = $appId; app_secret = $appSecret }
    $resp = Invoke-RestMethod `
        -Method Post `
        -Uri "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal" `
        -ContentType "application/json; charset=utf-8" `
        -Body $body

    if ($resp.code -ne 0) {
        throw "Feishu token error $($resp.code): $($resp.msg)"
    }

    return $resp.tenant_access_token
}

function Send-FeishuText {
    param(
        [string]$ChatId,
        [string]$Text
    )

    $token = Get-FeishuTenantToken
    $body = ConvertTo-JsonCompact @{
        receive_id = $ChatId
        msg_type = "text"
        content = ConvertTo-JsonCompact @{ text = $Text }
    }

    $resp = Invoke-RestMethod `
        -Method Post `
        -Uri "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id" `
        -Headers @{ Authorization = "Bearer $token" } `
        -ContentType "application/json; charset=utf-8" `
        -Body $body

    if ($resp.code -ne 0) {
        throw "Feishu send error $($resp.code): $($resp.msg)"
    }
}

function Get-TextFromContent {
    param([string]$Content)
    try {
        $json = $Content | ConvertFrom-Json
        return [string]$json.text
    }
    catch {
        return $Content
    }
}

function Build-Reply {
    param([string]$Text)
    if ($Text -match "measure|scan|tls|https|dns|tcp|security|headers|检测|测量|安全|响应头") {
        return "NetScope AI mock reply:`n已收到网络检测指令。真实后端启动后会调用 DeepSeek 与网络测量 Skill，返回 DNS/TCP/TLS/TTFB、安全响应头和风险评分。"
    }

    if ($Text -match "listen|listener|capture|webhook|监听|抓取|请求") {
        return "NetScope AI mock reply:`nWeb 监听指令已收到。真实后端会记录请求头、方法、路径、状态和风险提示，并支持导出 Excel/Word 报告。"
    }

    return "NetScope AI Feishu bot is connected.`nReceived: $Text`nTry: 检测 https://example.com, 对比两个网站, 开启 Web 监听, or 生成 Excel 报告."
}

Import-DotEnv $envPath

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://localhost:8090/")
$listener.Start()
Write-EventLog "server started on http://localhost:8090"
Write-Host "Feishu mock callback server listening on http://localhost:8090/"

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $request = $context.Request
        $response = $context.Response
        $response.ContentType = "application/json; charset=utf-8"

        $body = ""
        if ($request.HasEntityBody) {
            $reader = [System.IO.StreamReader]::new($request.InputStream, $request.ContentEncoding)
            $body = $reader.ReadToEnd()
            $reader.Close()
        }

        Write-EventLog "$($request.HttpMethod) $($request.Url.AbsolutePath) body=$body"

        if ($request.Url.AbsolutePath -eq "/api/feishu/health") {
            $json = ConvertTo-JsonCompact @{ status = "ok"; server = "mock"; hasFeishuCredential = [bool]($env:FEISHU_APP_ID -and $env:FEISHU_APP_SECRET) }
        }
        elseif ($request.Url.AbsolutePath -eq "/api/feishu/events" -and $body) {
            try {
                $payload = $body | ConvertFrom-Json
                if ($payload.type -eq "url_verification") {
                    $json = ConvertTo-JsonCompact @{ challenge = $payload.challenge }
                }
                elseif ($payload.header.event_type -eq "im.message.receive_v1") {
                    $chatId = [string]$payload.event.message.chat_id
                    $messageType = [string]$payload.event.message.message_type
                    $text = Get-TextFromContent ([string]$payload.event.message.content)

                    if ($chatId -and $messageType -eq "text") {
                        $reply = Build-Reply $text
                        try {
                            Send-FeishuText -ChatId $chatId -Text $reply
                            Write-EventLog "replied chat_id=$chatId text=$reply"
                        }
                        catch {
                            Write-EventLog "reply failed: $($_.Exception.Message)"
                        }
                    }
                    $json = ConvertTo-JsonCompact @{ status = "received"; eventType = "im.message.receive_v1" }
                }
                else {
                    $json = ConvertTo-JsonCompact @{ status = "received" }
                }
            }
            catch {
                Write-EventLog "event handling failed: $($_.Exception.Message)"
                $json = ConvertTo-JsonCompact @{ status = "received" }
            }
        }
        else {
            $response.StatusCode = 404
            $json = ConvertTo-JsonCompact @{ error = "not found" }
        }

        $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
        $response.OutputStream.Write($bytes, 0, $bytes.Length)
        $response.Close()
    }
}
finally {
    $listener.Stop()
}
