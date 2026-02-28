function collectPostLinks(maxPosts) {
    var posts = [];
    var seen = {};

    // Способ 1: ищем ссылки вида /p/CODE/
    var links = document.querySelectorAll('a[href]');
    for (var i = 0; i < links.length; i++) {
        var href = links[i].getAttribute('href');
        if (!href) continue;
        // Ссылка должна содержать /p/ в любом месте
        if (href.indexOf('/p/') === -1) continue;
        // Убираем query string и hash
        var clean = href.split('?')[0].split('#')[0];
        if (seen[clean]) continue;
        seen[clean] = true;
        posts.push(clean);
        if (posts.length >= maxPosts) break;
    }

    // Способ 2: если ничего не нашли — ищем через meta или data-атрибуты
    if (posts.length === 0) {
        var imgs = document.querySelectorAll('img[src*="instagram"]');
        for (var i = 0; i < imgs.length; i++) {
            var parent = imgs[i].parentElement;
            for (var j = 0; j < 5; j++) {
                if (!parent) break;
                var pHref = parent.getAttribute('href');
                if (pHref && pHref.indexOf('/p/') !== -1) {
                    var clean = pHref.split('?')[0].split('#')[0];
                    if (!seen[clean]) {
                        seen[clean] = true;
                        posts.push(clean);
                        if (posts.length >= maxPosts) break;
                    }
                }
                parent = parent.parentElement;
            }
            if (posts.length >= maxPosts) break;
        }
    }

    Android.onPostsFound(JSON.stringify(posts));
}

function collectLikesFromPost() {
    var btn = null;

    // Способ 1: ищем по aria-label
    var els = document.querySelectorAll('button, a, span, div');
    for (var i = 0; i < els.length; i++) {
        var lbl = (els[i].getAttribute('aria-label') || '').toLowerCase();
        if (lbl.indexOf('like') !== -1 || lbl.indexOf('нравится') !== -1) {
            btn = els[i];
            break;
        }
    }

    // Способ 2: ищем текст с числом и словом likes
    if (!btn) {
        var spans = document.querySelectorAll('span, a');
        for (var i = 0; i < spans.length; i++) {
            var txt = (spans[i].textContent || '').toLowerCase().trim();
            if ((txt.indexOf('like') !== -1 || txt.indexOf('нравится') !== -1)
                && txt.length < 50) {
                btn = spans[i];
                break;
            }
        }
    }

    if (btn) {
        btn.click();
        Android.onLikeButtonClicked('found');
    } else {
        Android.onLikeButtonClicked('not_found');
    }
}

function scrollAndCollectLikers() {
    var modal = null;

    // Способ 1: ищем dialog
    var dialogs = document.querySelectorAll('div[role="dialog"]');
    for (var i = 0; i < dialogs.length; i++) {
        if (dialogs[i].scrollHeight > dialogs[i].clientHeight + 10) {
            modal = dialogs[i];
            break;
        }
    }

    // Способ 2: ищем любой скроллируемый div с именами пользователей
    if (!modal) {
        var divs = document.querySelectorAll('div');
        for (var i = 0; i < divs.length; i++) {
            var d = divs[i];
            if (d.scrollHeight > d.clientHeight + 50 && d.scrollHeight > 200) {
                var style = window.getComputedStyle(d);
                var ov = style.overflowY;
                if (ov === 'auto' || ov === 'scroll') {
                    modal = d;
                    break;
                }
            }
        }
    }

    if (!modal) {
        Android.onLikersScrollResult('no_modal', '[]');
        return;
    }

    var prev = modal.scrollHeight;
    modal.scrollTo({ top: modal.scrollHeight, behavior: 'smooth' });

    var names = [];
    var seen = {};
    var links = modal.querySelectorAll('a[href]');
    for (var i = 0; i < links.length; i++) {
        var href = links[i].getAttribute('href');
        if (!href || href.indexOf('/') !== 0) continue;
        var parts = href.split('/').filter(function(p) { return p.length > 0; });
        if (parts.length !== 1) continue;
        var name = parts[0].toLowerCase();
        if (name.length < 1 || name.length > 30) continue;
        // Исключаем системные пути
        var skip = ['explore', 'reels', 'direct', 'stories', 'p', 'tv', 'reel'];
        if (skip.indexOf(name) !== -1) continue;
        if (!seen[name]) { seen[name] = true; names.push(name); }
    }

    setTimeout(function() {
        var more = modal.scrollHeight > prev;
        Android.onLikersScrollResult(more ? 'more' : 'end', JSON.stringify(names));
    }, 1500);
}

function scrollProfile() {
    window.scrollTo(0, 500);
    setTimeout(function() { window.scrollTo(0, 0); }, 800);
}
