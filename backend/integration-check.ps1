param(
    [string]$ApiBase = 'http://127.0.0.1:8081/api',
    [string]$WebBase = 'http://127.0.0.1:8081',
    [string]$MySqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [string]$MySqlUser = 'root',
    [string]$MySqlPassword = 'root',
    [string]$MySqlDatabase = 'firstapi'
)

$ErrorActionPreference = 'Stop'
$suffix = Get-Date -Format 'MMddHHmmss'
$results = New-Object System.Collections.Generic.List[object]

function Add-Result($page, $check, $result, $details) {
    $results.Add([pscustomobject]@{ Page = $page; Check = $check; Result = $result; Details = $details }) | Out-Null
}

function To-JsonBody($body) {
    if ($null -eq $body) { return $null }
    return ($body | ConvertTo-Json -Compress -Depth 8)
}

function Invoke-Api($method, $path, $body = $null) {
    $params = @{ Uri = "$ApiBase$path"; Method = $method }
    if ($null -ne $body) {
        $params.ContentType = 'application/json'
        $params.Body = To-JsonBody $body
    }
    return Invoke-RestMethod @params
}

function Invoke-DbText($sql) {
    $stdout = Join-Path $env:TEMP ('mysql-out-' + [guid]::NewGuid().ToString() + '.txt')
    $stderr = Join-Path $env:TEMP ('mysql-err-' + [guid]::NewGuid().ToString() + '.txt')
    try {
        $dbArgs = ('-h127.0.0.1 -u{0} -p{1} -N -B {2} -e "{3}"' -f $MySqlUser, $MySqlPassword, $MySqlDatabase, $sql)
        $proc = Start-Process -FilePath $MySqlExe -ArgumentList $dbArgs -Wait -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        if ($proc.ExitCode -ne 0) {
            throw ("MySQL query failed: " + $sql)
        }
        return ((Get-Content -Path $stdout -Raw -ErrorAction SilentlyContinue) | Out-String).Trim()
    } finally {
        if (Test-Path $stdout) { Remove-Item $stdout -Force }
        if (Test-Path $stderr) { Remove-Item $stderr -Force }
    }
}

function Invoke-DbScalar($sql) {
    return (Invoke-DbText $sql).Trim()
}

function Assert-Contains($text, $needle, $message) {
    if ($text -notmatch [regex]::Escape($needle)) { throw $message }
}

$requiredTables = @(
    'users', 'groups', 'subscriptions', 'accounts', 'announcements', 'ips',
    'api_keys', 'settings', 'profiles', 'my_subscription'
)
try {
    $tables = Invoke-DbText 'show tables'
    foreach ($table in $requiredTables) {
        Assert-Contains $tables $table "Missing table $table"
    }
    Add-Result 'Database' 'schema' 'PASS' 'Required MySQL tables exist'
} catch {
    Add-Result 'Database' 'schema' 'FAIL' $_.Exception.Message
}

$spaRoutes = @(
    '/', '/monitor', '/users', '/groups', '/subscriptions', '/accounts', '/announcements', '/ips',
    '/records', '/settings', '/my-api-keys', '/my-records',
    '/my-subscription', '/profile'
)
foreach ($route in $spaRoutes) {
    try {
        $page = Invoke-WebRequest -Uri "$WebBase$route" -UseBasicParsing
        if ($page.StatusCode -eq 200 -and $page.Content -match '<div id="root"></div>') {
            Add-Result 'SPA' "route $route" 'PASS' 'Spring Boot SPA fallback ok'
        } else {
            Add-Result 'SPA' "route $route" 'FAIL' 'Unexpected SPA response'
        }
    } catch {
        Add-Result 'SPA' "route $route" 'FAIL' $_.Exception.Message
    }
}

$readonlyChecks = @(
    @{ Page='Dashboard'; Path='/admin/dashboard'; Probe='stats' },
    @{ Page='Monitor'; Path='/admin/monitor'; Probe='stats' },
    @{ Page='Records'; Path='/admin/records'; Probe='records' },
    @{ Page='MyRecords'; Path='/user/records'; Probe='records' }
)
foreach ($check in $readonlyChecks) {
    try {
        $res = Invoke-Api 'Get' $check.Path
        if ($null -ne $res.data.($check.Probe)) {
            Add-Result $check.Page 'GET' 'PASS' 'Response shape ok'
        } else {
            Add-Result $check.Page 'GET' 'FAIL' 'Missing expected field'
        }
    } catch {
        Add-Result $check.Page 'GET' 'FAIL' $_.Exception.Message
    }
}

try {
    $createMarker = "itest-user-$suffix"
    $updateMarker = "itest-user-updated-$suffix"
    $created = Invoke-Api 'Post' '/admin/users' @{ username=$createMarker; email="$suffix@example.com"; group='VIP'; password='123456' }
    $id = $created.data.id
    if ([int](Invoke-DbScalar "select count(*) from users where username = '$createMarker'") -lt 1) { throw 'Users create not written to MySQL' }
    Invoke-Api 'Put' "/admin/users/$id" @{ username=$updateMarker; group='Enterprise'; status='ok' } | Out-Null
    if ([int](Invoke-DbScalar "select count(*) from users where id = $id and username = '$updateMarker'") -ne 1) { throw 'Users update not written to MySQL' }
    Invoke-Api 'Delete' "/admin/users/$id" | Out-Null
    if ([int](Invoke-DbScalar "select count(*) from users where id = $id") -ne 0) { throw 'Users delete not written to MySQL' }
    Add-Result 'Users' 'CRUD' 'PASS' "id=$id"
} catch {
    Add-Result 'Users' 'CRUD' 'FAIL' $_.Exception.Message
}

try {
    $createMarker = "itest-group-$suffix"
    $updateMarker = "itest-group-updated-$suffix"
    $created = Invoke-Api 'Post' '/admin/groups' @{ name=$createMarker; priority='33'; rate='0.7x'; billingType='metered' }
    $id = $created.data.id
    if ([int](Invoke-DbScalar ('select count(*) from `groups` where name = ''' + $createMarker + '''')) -lt 1) { throw 'Groups create not written to MySQL' }
    Invoke-Api 'Put' "/admin/groups/$id" @{ name=$updateMarker; rate='0.6x'; status='active' } | Out-Null
    if ([int](Invoke-DbScalar ('select count(*) from `groups` where id = ' + $id + ' and name = ''' + $updateMarker + '''')) -ne 1) { throw 'Groups update not written to MySQL' }
    Invoke-Api 'Delete' "/admin/groups/$id" | Out-Null
    if ([int](Invoke-DbScalar ('select count(*) from `groups` where id = ' + $id)) -ne 0) { throw 'Groups delete not written to MySQL' }
    Add-Result 'Groups' 'CRUD' 'PASS' "id=$id"
} catch {
    Add-Result 'Groups' 'CRUD' 'FAIL' $_.Exception.Message
}

try {
    $createMarker = "itest-sub-$suffix@example.com"
    $updateMarker = "itest-sub-updated-$suffix@example.com"
    $created = Invoke-Api 'Post' '/admin/subscriptions' @{ user=$createMarker; group='Claude Pro'; quota='200'; expiry='2026/12/31' }
    $id = $created.data.id
    if ([int](Invoke-DbScalar "select count(*) from subscriptions where user_name = '$createMarker'") -lt 1) { throw 'Subscriptions create not written to MySQL' }
    if ([int](Invoke-DbScalar ('select count(*) from subscriptions where id = ' + $id + ' and usage_text = ''¥0.00 / ¥200''')) -ne 1) { throw 'Subscriptions quota not written to MySQL' }
    Invoke-Api 'Put' "/admin/subscriptions/$id" @{ user=$updateMarker; group='Enterprise Gold'; quota='500'; status='ok' } | Out-Null
    if ([int](Invoke-DbScalar ('select count(*) from subscriptions where id = ' + $id + ' and user_name = ''' + $updateMarker + ''' and usage_text = ''¥0.00 / ¥500''')) -ne 1) { throw 'Subscriptions update not written to MySQL' }
    Invoke-Api 'Delete' "/admin/subscriptions/$id" | Out-Null
    if ([int](Invoke-DbScalar "select count(*) from subscriptions where id = $id") -ne 0) { throw 'Subscriptions delete not written to MySQL' }
    Add-Result 'Subscriptions' 'CRUD' 'PASS' "id=$id"
} catch {
    Add-Result 'Subscriptions' 'CRUD' 'FAIL' $_.Exception.Message
}

try {
    $createMarker = "itest-account-$suffix"
    $updateMarker = "itest-account-updated-$suffix"
    $created = Invoke-Api 'Post' '/admin/accounts' @{ name=$createMarker; platform='OpenAI'; credentials="sk-test-$suffix" }
    $id = $created.data.id
    if ([int](Invoke-DbScalar "select count(*) from accounts where name = '$createMarker'") -lt 1) { throw 'Accounts create not written to MySQL' }
    Invoke-Api 'Put' "/admin/accounts/$id" @{ name=$updateMarker; platform='Anthropic' } | Out-Null
    if ([int](Invoke-DbScalar "select count(*) from accounts where id = $id and name = '$updateMarker' and platform = 'Anthropic'" ) -ne 1) { throw 'Accounts update not written to MySQL' }
    Invoke-Api 'Post' "/admin/accounts/$id/test" | Out-Null
    if ([int](Invoke-DbScalar ('select count(*) from accounts where id = ' + $id + ' and error_count = 0')) -ne 1) { throw 'Accounts test result not written to MySQL' }
    Invoke-Api 'Delete' "/admin/accounts/$id" | Out-Null
    if ([int](Invoke-DbScalar "select count(*) from accounts where id = $id") -ne 0) { throw 'Accounts delete not written to MySQL' }
    Add-Result 'Accounts' 'CRUD+Test' 'PASS' "id=$id"
} catch {
    Add-Result 'Accounts' 'CRUD+Test' 'FAIL' $_.Exception.Message
}

try {
    $createMarker = "itest-ann-$suffix"
    $updateMarker = "itest-ann-updated-$suffix"
    $created = Invoke-Api 'Post' '/admin/announcements' @{ title=$createMarker; content="content-$suffix"; type='notice'; target='all' }
    $id = $created.data.id
    if ([int](Invoke-DbScalar "select count(*) from announcements where title = '$createMarker'") -lt 1) { throw 'Announcements create not written to MySQL' }
    Invoke-Api 'Put' "/admin/announcements/$id" @{ title=$updateMarker; status='archived' } | Out-Null
    if ([int](Invoke-DbScalar "select count(*) from announcements where id = $id and title = '$updateMarker' and status_name = 'archived'") -ne 1) { throw 'Announcements update not written to MySQL' }
    Invoke-Api 'Delete' "/admin/announcements/$id" | Out-Null
    if ([int](Invoke-DbScalar "select count(*) from announcements where id = $id") -ne 0) { throw 'Announcements delete not written to MySQL' }
    Add-Result 'Announcements' 'CRUD' 'PASS' "id=$id"
} catch {
    Add-Result 'Announcements' 'CRUD' 'FAIL' $_.Exception.Message
}

try {
    $createMarker = "itest-ip-$suffix"
    $updateMarker = "itest-ip-updated-$suffix"
    $created = Invoke-Api 'Post' '/admin/ips' @{ name=$createMarker; protocol='SOCKS5'; address='10.0.0.1:9000' }
    $id = $created.data.id
    if ([int](Invoke-DbScalar "select count(*) from ips where name = '$createMarker'") -lt 1) { throw 'IPs create not written to MySQL' }
    Invoke-Api 'Put' "/admin/ips/$id" @{ name=$updateMarker; address='10.0.0.2:9001' } | Out-Null
    if ([int](Invoke-DbScalar "select count(*) from ips where id = $id and name = '$updateMarker' and address = '10.0.0.2:9001'") -ne 1) { throw 'IPs update not written to MySQL' }
    Invoke-Api 'Post' "/admin/ips/$id/test" | Out-Null
    if ([int](Invoke-DbScalar "select count(*) from ips where id = $id and latency = '128ms'") -ne 1) { throw 'IPs test not written to MySQL' }
    Invoke-Api 'Delete' "/admin/ips/$id" | Out-Null
    if ([int](Invoke-DbScalar "select count(*) from ips where id = $id") -ne 0) { throw 'IPs delete not written to MySQL' }
    Add-Result 'IPs' 'CRUD+Test' 'PASS' "id=$id"
} catch {
    Add-Result 'IPs' 'CRUD+Test' 'FAIL' $_.Exception.Message
}

try {
    $createMarker = "itest-key-$suffix"
    $created = Invoke-Api 'Post' '/user/api-keys' @{ name=$createMarker }
    $id = $created.data.id
    if ([int](Invoke-DbScalar "select count(*) from api_keys where name = '$createMarker'") -lt 1) { throw 'MyApiKeys create not written to MySQL' }
    $rotated = Invoke-Api 'Post' "/user/api-keys/$id/rotate"
    $newKey = $rotated.data.key
    if ([int](Invoke-DbScalar "select count(*) from api_keys where id = $id and api_key = '$newKey'") -ne 1) { throw 'MyApiKeys rotate not written to MySQL' }
    Invoke-Api 'Delete' "/user/api-keys/$id" | Out-Null
    if ([int](Invoke-DbScalar "select count(*) from api_keys where id = $id") -ne 0) { throw 'MyApiKeys delete not written to MySQL' }
    Add-Result 'MyApiKeys' 'Create+Rotate+Delete' 'PASS' "id=$id"
} catch {
    Add-Result 'MyApiKeys' 'Create+Rotate+Delete' 'FAIL' $_.Exception.Message
}

try {
    $settings = Invoke-Api 'Get' '/admin/settings'
    $marker = "itest-settings-$suffix"
    Invoke-Api 'Put' '/admin/settings' @{ siteAnnouncement=$marker } | Out-Null
    if ((Invoke-DbScalar 'select site_announcement from settings where id = 1') -ne $marker) { throw 'Settings update not written to MySQL' }
    Invoke-Api 'Put' '/admin/settings' @{ siteAnnouncement=$settings.data.siteAnnouncement } | Out-Null
    Add-Result 'Settings' 'Update' 'PASS' 'settings persisted and restored'
} catch {
    Add-Result 'Settings' 'Update' 'FAIL' $_.Exception.Message
}

try {
    $profile = Invoke-Api 'Get' '/user/profile'
    $marker = "itest-profile-$suffix"
    Invoke-Api 'Put' '/user/profile' @{ username=$profile.data.username; phone=$marker; bio=$marker } | Out-Null
    $profileRow = Invoke-DbScalar 'select concat_ws(''|'', phone, bio, two_factor_enabled) from profiles where id = 1'
    Assert-Contains $profileRow $marker 'Profile update not written to MySQL'
    Invoke-Api 'Post' '/user/profile/enable-2fa' | Out-Null
    $profileRow = Invoke-DbScalar 'select concat_ws(''|'', phone, bio, two_factor_enabled) from profiles where id = 1'
    Assert-Contains $profileRow '|1' 'Profile 2FA not written to MySQL'
    Add-Result 'Profile' 'Update+2FA' 'PASS' 'profile persisted'
} catch {
    Add-Result 'Profile' 'Update+2FA' 'FAIL' $_.Exception.Message
}

try {
    $before = (Invoke-Api 'Get' '/user/subscription').data.history.Count
    $after = Invoke-Api 'Post' '/user/subscription/renew'
    if ($after.data.history.Count -lt ($before + 1)) { throw 'MySubscription history count did not increase' }
    $today = Get-Date -Format 'yyyy/MM/dd'
    Assert-Contains (Invoke-DbScalar 'select history_json from my_subscription where id = 1') $today 'MySubscription history not written to MySQL'
    Add-Result 'MySubscription' 'Renew' 'PASS' 'renew history persisted'
} catch {
    Add-Result 'MySubscription' 'Renew' 'FAIL' $_.Exception.Message
}

$results | Format-Table -AutoSize
if (($results | Where-Object { $_.Result -eq 'FAIL' }).Count -gt 0) { exit 1 }
