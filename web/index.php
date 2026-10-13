<?php
declare(strict_types=1);

const REFRESH_SECONDS = 1800;                 // 30 دقيقة (ثابت)
const DEFAULT_URL     = 'https://dreams.xo.je/';

/**
 * يقرأ ترويسات الاستجابة ويحدد هل الموقع يمنع العرض داخل iframe.
 * يعيد [bool|null $embeddable, string $reason]
 */
function frame_policy(array $headers, bool $sameOrigin = false): array
{
    if (isset($headers['x-frame-options'])) {
        if (!($sameOrigin && stripos($headers['x-frame-options'], 'SAMEORIGIN') !== false)) {
            return [false, 'X-Frame-Options: ' . $headers['x-frame-options']];
        }
    }
    if (isset($headers['content-security-policy'])
        && preg_match('/frame-ancestors\s+([^;]*)/i', $headers['content-security-policy'], $m)
        && strpos($m[1], '*') === false
        && !($sameOrigin && stripos($m[1], "'self'") !== false)) {
        return [false, 'CSP frame-ancestors: ' . trim($m[1])];
    }
    return [true, ''];
}

function is_public_ip(string $ip): bool
{
    return (bool) filter_var($ip, FILTER_VALIDATE_IP,
        FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE);
}

/** يجلب ترويسات الرابط فقط (بدون الجسم) مع تتبع التحويلات يدويًا. */
function fetch_headers(string $url): ?array
{
    for ($hop = 0; $hop < 5; $hop++) {
        $p = parse_url($url);
        if (!$p || empty($p['host']) || !in_array($p['scheme'] ?? '', ['http', 'https'], true)) {
            return null;
        }
        $ip = gethostbyname($p['host']);
        if (!is_public_ip($ip)) {
            return null; // حماية من الوصول لعناوين داخلية
        }
        $headers = [];
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_TIMEOUT        => 8,
            CURLOPT_CONNECTTIMEOUT => 5,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_USERAGENT      => 'Mozilla/5.0 (compatible; MiniBrowser)',
            CURLOPT_HEADERFUNCTION => function ($c, $line) use (&$headers) {
                $pos = strpos($line, ':');
                if ($pos !== false) {
                    $headers[strtolower(trim(substr($line, 0, $pos)))] = trim(substr($line, $pos + 1));
                }
                return strlen($line);
            },
            CURLOPT_WRITEFUNCTION  => function () { return 0; }, // أوقف التحميل بعد الترويسات
        ]);
        curl_exec($ch);
        $code = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($code === 0) {
            return null;
        }
        if ($code >= 300 && $code < 400 && !empty($headers['location'])) {
            $next = $headers['location'];
            if (!preg_match('#^https?://#i', $next)) {
                $base = $p['scheme'] . '://' . $p['host'] . (isset($p['port']) ? ':' . $p['port'] : '');
                $next = $base . '/' . ltrim($next, '/');
            }
            $url = $next;
            continue;
        }
        return $headers;
    }
    return null;
}

// نقطة فحص: هل يسمح الموقع بالعرض داخل iframe؟
if (isset($_GET['check'])) {
    header('Content-Type: application/json; charset=utf-8');
    $h = fetch_headers((string) ($_GET['url'] ?? ''));
    if ($h === null) {
        echo json_encode(['embeddable' => null]);
    } else {
        $target = (string) parse_url((string) $_GET['url'], PHP_URL_HOST);
        $mine   = preg_replace('/:\d+$/', '', (string) ($_SERVER['HTTP_HOST'] ?? ''));
        [$ok, $reason] = frame_policy($h, strcasecmp($target, $mine) === 0);
        echo json_encode(['embeddable' => $ok, 'reason' => $reason]);
    }
    exit;
}
?>
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>متصفح مصغر</title>
<style>
  *{box-sizing:border-box}
  html,body{height:100%;margin:0;font-family:system-ui,Tahoma,sans-serif;background:#f3f4f6}
  body{display:flex;flex-direction:column;height:100dvh}
  form{display:flex;gap:8px;padding:8px;background:#fff;border-bottom:1px solid #ddd}
  input{flex:1;min-width:0;padding:10px;font-size:16px;border:1px solid #bbb;border-radius:8px;direction:ltr;text-align:left}
  button{padding:10px 18px;font-size:16px;border:0;border-radius:8px;background:#2563eb;color:#fff;cursor:pointer}
  #msg{padding:12px;text-align:center;color:#333;display:none}
  #msg button{margin-top:8px}
  iframe{flex:1;width:100%;border:0;background:#fff;display:none}
</style>
</head>
<body>
<form id="f">
  <input id="url" type="text" inputmode="url" dir="ltr" value="<?= htmlspecialchars(DEFAULT_URL, ENT_QUOTES) ?>" placeholder="https://">
  <button type="submit">فتح</button>
</form>
<div id="msg"></div>
<iframe id="frame" referrerpolicy="no-referrer-when-downgrade"></iframe>

<script>
(function () {
  var INTERVAL = <?= REFRESH_SECONDS ?> * 1000;
  var input = document.getElementById('url');
  var frame = document.getElementById('frame');
  var msg   = document.getElementById('msg');
  var current = '', popup = null, nextAt = 0, timer = null;

  function normalize(v) {
    v = v.trim();
    if (!v) return '';
    if (!/^https?:\/\//i.test(v)) v = 'https://' + v;
    try { return new URL(v).href; } catch (e) { return ''; }
  }

  function startTimer() {
    nextAt = Date.now() + INTERVAL;
    clearInterval(timer);
    timer = setInterval(function () {
      if (Date.now() < nextAt) return;
      nextAt = Date.now() + INTERVAL;
      if (popup) {
        if (popup.closed) { clearInterval(timer); return; }
        popup.location.href = current;      // إعادة تحميل كاملة للنافذة
      } else {
        frame.src = current;                // إعادة تحميل كاملة للصفحة داخل الإطار
      }
    }, 1000);
  }

  function showFrame() {
    msg.style.display = 'none';
    frame.style.display = 'block';
    popup = null;
    frame.src = current;
    startTimer();
  }

  function showBlocked() {
    frame.style.display = 'none';
    frame.src = 'about:blank';
    msg.style.display = 'block';
    msg.innerHTML = 'هذا الموقع يمنع عرضه داخل صفحة أخرى.<br>افتحه في نافذة مستقلة (أبقِ هذه الصفحة مفتوحة ليستمر التحديث).<br>';
    var b = document.createElement('button');
    b.textContent = 'فتح في نافذة';
    b.onclick = function () {
      popup = window.open(current, '_blank');
      if (popup) { msg.style.display = 'none'; startTimer(); }
      else { msg.textContent = 'تم حظر النافذة المنبثقة، اسمح بها ثم أعد المحاولة.'; }
    };
    msg.appendChild(b);
  }

  document.getElementById('f').addEventListener('submit', function (e) {
    e.preventDefault();
    var u = normalize(input.value);
    if (!u) return;
    current = u;
    input.value = u;
    clearInterval(timer);
    fetch('?check=1&url=' + encodeURIComponent(u))
      .then(function (r) { return r.json(); })
      .then(function (d) { d.embeddable === false ? showBlocked() : showFrame(); })
      .catch(showFrame);
  });
})();
</script>
</body>
</html>
