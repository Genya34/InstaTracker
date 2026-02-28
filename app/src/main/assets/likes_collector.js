function collectPostLinks(maxPosts) {
    var links = document.querySelectorAll('a[href]');
    var posts = [];
    var seen = {};
    for (var i = 0; i < links.length; i++) {
        var href = links[i].getAttribute('href');
        if (!href) continue;
        if (href.indexOf('/p/') !== 0) continue;
        if (seen[href]) continue;
        seen[href] = true;
        posts.push(href);
        if (posts.length >= maxPosts) break;
    }
    Android.onPostsFound(JSON.stringify(posts));
}

function collectLikesFromPost() {
    var btn = null;
    var els = document.querySelectorAll('button, a, span');
    for (var i = 0; i < els.length; i++) {
        var lbl = (els[i].getAttribute('aria-label') || '').toLowerCase();
        var txt = (els[i].textContent || '').toLowerCase();
        if (lbl.indexOf('like') !== -1 || lbl.indexOf('нравится') !== -1 ||
            txt.indexOf(' likes') !== -1 || txt.indexOf('нравится') !== -1) {
            btn = els[i];
            break;
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
    var divs = document.querySelectorAll('div');
    for (var i = 0; i < divs.length; i++) {
        var role = divs[i].getAttribute('role');
        if (role === 'dialog' && divs[i].scrollHeight > divs[i].clientHeight + 10) {
            modal = divs[i];
            break;
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
        if (!seen[name]) { seen[name] = true; names.push(name); }
    }
    setTimeout(function() {
        var more = modal.scrollHeight > prev;
        Android.onLikersScrollResult(more ? 'more' : 'end', JSON.stringify(names));
    }, 1500);
}

function scrollProfile() {
    window.scrollTo(0, 300);
    setTimeout(function() { window.scrollTo(0, 0); }, 500);
}
