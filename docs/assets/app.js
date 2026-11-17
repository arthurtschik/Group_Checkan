// Holt beim Öffnen das neueste Release von GitHub und setzt Version, Größe und den direkten APK-Link.
// „Was ist neu?“ steht beim Veröffentlichen eines Releases schon fest in der Seite (build/site.py).
// Dieses Skript ersetzt den Text nur, wenn auf GitHub eine andere Version liegt als die eingebaute.
// Ohne JavaScript oder bei einem Fehler bleiben der eingebaute Text und der feste „latest“-Link.
(function () {
  "use strict";
  // Feste Repository-ID: bleibt gleich, auch wenn das Repository umbenannt wird.
  var API = "https://api.github.com/repositories/1462092325/releases/latest";
  var OWNER = "https://github.com/arthurtschik/";
  var RELEASES = OWNER + "Group_Checkan/releases/latest";

  var btn = document.getElementById("dl");
  var meta = document.getElementById("meta");
  var newsTitle = document.getElementById("newsTitle");
  var newsBody = document.getElementById("newsBody");
  var newsBox = document.getElementById("news");
  var baked = newsBox ? newsBox.getAttribute("data-version") : "";   // Version der eingebauten Neuerungen
  if (!btn || !meta) return;

  // Links aus Release-Texten nur für dieses Konto zulassen (nie javascript: oder fremde Adressen).
  function safeUrl(u) { return typeof u === "string" && u.toLowerCase().indexOf(OWNER.toLowerCase()) === 0; }

  // ---------- „Was ist neu?“ ----------
  // Alles wird als Text eingesetzt (nie als HTML). Unterstützt: **fett**, *kursiv*, `code`, [Text](Link).
  var TOKEN = /(\*\*[^*]+\*\*|\*[^*\s][^*]*\*|`[^`]+`|\[[^\]]+\]\([^)\s]+\))/g;
  function rich(el, text) {
    text.split(TOKEN).forEach(function (part) {
      if (!part) return;
      var n, m;
      if (/^\*\*[^*]+\*\*$/.test(part)) { n = document.createElement("strong"); n.textContent = part.slice(2, -2); }
      else if (/^\*[^*\s][^*]*\*$/.test(part)) { n = document.createElement("em"); n.textContent = part.slice(1, -1); }
      else if (/^`[^`]+`$/.test(part)) { n = document.createElement("code"); n.textContent = part.slice(1, -1); }
      else if ((m = /^\[([^\]]+)\]\(([^)\s]+)\)$/.exec(part))) {
        if (safeUrl(m[2])) { n = document.createElement("a"); n.href = m[2]; n.rel = "noopener"; n.textContent = m[1]; }
        else n = document.createTextNode(m[1]);
      } else n = document.createTextNode(part);
      el.appendChild(n);
    });
  }

  function moreLink(url) {
    var a = document.createElement("a");
    a.className = "more";
    a.href = safeUrl(url) ? url : RELEASES;
    a.rel = "noopener";
    a.textContent = "Alle Release-Notizen";
    return a;
  }

  function newsFallback(url) {
    if (!newsBody) return;
    if (newsTitle) newsTitle.textContent = "Was ist neu?";
    newsBody.textContent = "Die Neuerungen findest du bei den Release-Notizen auf GitHub. ";
    newsBody.appendChild(moreLink(url));
  }

  // Abschnitte, die nicht mehr zu den Neuerungen gehören: Update-Hinweis, Signatur, Haftungshinweis, „Was bisher geschah“.
  var STOP_HEADING = /^(Update|Signatur|Prüfsummen?|Hinweis|Was bisher\b.*)\s*$/i;

  function showNews(rel) {
    if (!newsBody || !newsTitle) return;
    var lines = String(rel.body || "").slice(0, 20000).split(/\r?\n/);
    var items = [];          // { t: "h" | "p" | "li", text }
    var cur = null;          // Absatz/Listenpunkt, an den umbrochene Folgezeilen angehängt werden
    var inFence = false, firstHeading = true;
    function add(t, text) { cur = { t: t, text: text }; items.push(cur); }
    for (var i = 0; i < lines.length && items.length < 80; i++) {
      var l = lines[i];
      if (/^\s*```/.test(l)) { inFence = !inFence; cur = null; continue; }
      if (inFence) continue;
      if (/^\s*-{3,}\s*$/.test(l)) break;                       // erste Trennlinie beendet die Neuerungen
      if (!l.trim()) { cur = null; continue; }                  // Leerzeile beendet den Absatz
      var h = /^#{1,4}\s+(.*)$/.exec(l);
      if (h) {
        cur = null;
        var text = h[1].trim();
        if (STOP_HEADING.test(text.replace(/^[^A-Za-zÄÖÜäöüß0-9]+/, ""))) break;
        if (firstHeading) { firstHeading = false; continue; }   // Haupttitel steht schon im Aufklapp-Titel
        add("h", text);
        continue;
      }
      var b = /^\s*[-*]\s+(.*)$/.exec(l);
      if (b) { add("li", b[1].trim()); continue; }
      var q = l.replace(/^\s*>\s?/, "").trim();
      if (!q) { cur = null; continue; }
      if (cur && cur.t !== "h") cur.text += " " + q;            // Zeile gehört zum selben Absatz/Punkt
      else add("p", q);
    }
    if (!items.length) return newsFallback(rel.html_url);

    var tag = String(rel.tag_name || "");
    var version = /^v?\d+(\.\d+){0,3}$/i.test(tag) ? tag.replace(/^v/i, "") : "";
    newsTitle.textContent = version ? "Was ist neu in " + version + "?" : "Was ist neu?";
    newsBody.textContent = "";
    var ul = null;
    items.forEach(function (it) {
      if (it.t === "li") {
        if (!ul) { ul = document.createElement("ul"); newsBody.appendChild(ul); }
        var li = document.createElement("li"); rich(li, it.text); ul.appendChild(li);
        return;
      }
      ul = null;
      var p = document.createElement("p");
      if (it.t === "h") p.className = "nh";
      rich(p, it.text);
      newsBody.appendChild(p);
    });
    newsBody.appendChild(moreLink(rel.html_url));
  }

  // ---------- Release abfragen ----------
  function fallback() {
    if (!meta.getAttribute("data-baked")) meta.textContent = "Die aktuelle Version findest du über den Knopf.";
    if (!baked) newsFallback();
  }
  if (!window.fetch) { fallback(); return; }

  var ctrl = typeof AbortController === "function" ? new AbortController() : null;
  var timer = ctrl ? setTimeout(function () { ctrl.abort(); }, 8000) : null;

  fetch(API, {
    headers: { Accept: "application/vnd.github+json" },
    referrerPolicy: "no-referrer",
    signal: ctrl ? ctrl.signal : undefined
  })
    .then(function (r) { if (!r.ok) throw new Error(String(r.status)); return r.json(); })
    .then(function (rel) {
      // Nur .apk-Dateien aus Releases dieses Kontos werden als Download-Link übernommen.
      var apk = (rel.assets || []).filter(function (a) {
        var u = a && a.browser_download_url;
        return typeof u === "string" && /\.apk$/i.test(u) && safeUrl(u) && u.indexOf("/releases/download/") > 0;
      })[0];
      if (!apk) throw new Error("keine APK");
      btn.href = apk.browser_download_url;

      // nur echte Versionsnummern anzeigen (z. B. v2.7), sonst weglassen
      var tag = String(rel.tag_name || "");
      var version = /^v?\d+(\.\d+){0,3}$/i.test(tag) ? tag.replace(/^v/i, "") : "";
      var mb = (Number(apk.size) / 1048576).toFixed(1).replace(".", ",");
      var date = rel.published_at ? new Date(rel.published_at).toLocaleDateString("de-DE") : "";
      var parts = [];
      if (version) parts.push("Version " + version);
      if (isFinite(Number(apk.size)) && Number(apk.size) > 0) parts.push(mb + " MB");
      if (date && date !== "Invalid Date") parts.push(date);
      meta.textContent = parts.length ? parts.join(" · ") : "Aktuelle Version bereit zum Download";

      // eingebaute Neuerungen nur ersetzen, wenn GitHub eine andere Version hat
      if (!baked || (version && version !== baked)) showNews(rel);
    })
    .catch(fallback)
    .then(function () { if (timer) clearTimeout(timer); });
})();
