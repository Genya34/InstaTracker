function collectPostLinks(maxPosts) {
    var posts = [];
    var seen = {};

    var links = document.querySelectorAll('a[href]');
    for (var i = 0; i < links.length; i++) {
        var href = links[i].getAttribute('href');
        if (!href) continue;
        if (href.indexOf('/p/') === -1) continue;
        var clean = href.split('?')[0].split('#')[0];
        // Убираем trailing slash и добавляем /liked_by/
        clean = clean.replace(/\/$/, '');
        var likedByUrl = clean + '/liked_by/';
        if (seen[likedByUrl]) continue;
        seen[likedByUrl] = true;
        posts.push(likedByUrl);
        if (posts.length >= maxPosts) break;
    }

    if (posts.length === 0) {
        var imgs = document.querySelectorAll('img');
        for (var i = 0; i < imgs.length; i++) {
            var parent = imgs[i].parentElement;
            for (var j = 0; j < 6; j++) {
                if (!parent) break;
                var pHref = parent.getAttribute('href');
                if (pHref && pHref.indexOf('/p/') !== -1) {
                    var clean = pHref.split('?')[0].split('#')[0].replace(/\/$/, '');
                    var likedByUrl = clean + '/liked_by/';
                    if (!seen[likedByUrl]) {
                        seen[likedByUrl] = true;
                        posts.push(likedByUrl);
                    }
                    break;
                }
                parent = parent.parentElement;
            }
            if (posts.length >= maxPosts) break;
        }
    }

    Android.onPostsFound(JSON.stringify(posts));
}

// Эта функция больше не нужна — но оставляем чтобы не ломать вызовы
function collectLikesFromPost() {
    Android.onLikeButtonClicked('found');
}

function scrollAndCollectLikers() {
    // Страница /liked_by/ — это обычная страница со списком пользователей
    // Прокручиваем её и собираем имена
    window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });

    var prev = document.body.scrollHeight;
    var names = [];
    var seen = {};
    var skip = ['explore', 'reels', 'direct', 'stories', 'p', 'tv', 'reel',
                'accounts', 'about', 'privacy', 'terms', 'help', 'press', 'liked_by'];

    var links = document.querySelectorAll('a[href]');
    for (var i = 0; i < links.length; i++) {
        var href = links[i].getAttribute('href');
        if (!href || href.indexOf('/') !== 0) continue;
        var parts = href.split('/').filter(function(p) { return p.length > 0; });
        if (parts.length !== 1) continue;
        var name = parts[0].toLowerCase();
        if (name.length < 1 || name.length > 30) continue;
        if (skip.indexOf(name) !== -1) continue;
        if (!seen[name]) { seen[name] = true; names.push(name); }
    }

    setTimeout(function() {
        var more = document.body.scrollHeight > prev;
        Android.onLikersScrollResult(more ? 'more' : 'end', JSON.stringify(names));
    }, 1500);
}

function scrollProfile() {
    window.scrollTo(0, 500);
    setTimeout(function() { window.scrollTo(0, 0); }, 800);
}
