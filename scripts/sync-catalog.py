"""Refresh curated, complete open films from the official Blender video API.

Run from the repository root: python scripts/sync-catalog.py
Only metadata and poster artwork are downloaded; video playback uses the official host.
"""
import concurrent.futures
import json
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
HOST = 'https://video.blender.org'
# Chinese display names are localized labels; original titles remain in the credits.
FILMS = [
    ('spring', '3d95fb3d-c866-42c8-9db1-fe82f48ccb95', '春日', '奇幻', '奇幻,自然,无对白', 2019,
     '牧羊女孩和小狗走进山谷，在古老精灵苏醒之前，为大地唤回春天。', 'Andy Goralczyk · Blender Animation Studio', 'CC BY-SA 4.0'),
    ('caminandes', '23f3ef79-15dc-44c5-aa45-cf92e78a4509', '羊驼与企鹅', '喜剧', '喜剧,友谊,无对白', 2016,
     '寒冬的巴塔哥尼亚，一只羊驼与一只企鹅，为最后一颗浆果展开了一场出人意料的争夺。原作系列第三部，独立完整故事。', 'Pablo Vazquez · Blender Institute', 'CC BY 3.0'),
    ('charge', '04da454b-9893-4184-98f3-248d00625efe', '充电', '科幻', '科幻,动作,无对白', 2022,
     '能源耗尽的工业世界里，一位老人潜入自动化工厂，想要为自己的生命延续争取一线机会。', 'Hjalti Hjalmarsson · Blender Studio', 'CC BY 4.0'),
    ('sintel', '0eb052d0-fd51-43e6-aa33-ecdbf77a5d40', '辛特尔', '冒险', '冒险,巨龙,原声', 2010,
     '少女辛特尔救下了一只受伤的小龙。为了寻找失散的伙伴，她跨越荒原与雪山，追寻一个令人心碎的答案。', 'Colin Levy · Blender Foundation', 'CC BY 3.0'),
    ('coffee-run', 'ff8fe61b-026f-4f07-b66b-2a790d6f6ab1', '咖啡之旅', '治愈', '回忆,人生,无对白', 2020,
     '一杯咖啡串起一个人的回忆。奔跑、跌落和重新出发，化为一场色彩斑斓的人生旅途。', 'Hjalti Hjalmarsson · Blender Animation Studio', 'CC BY 4.0'),
    ('big-buck-bunny', 'bf1f3fb5-b119-4f9f-9930-8e20e892b898', '大雄兔', '喜剧', '喜剧,森林,无对白', 2008,
     '森林中的大兔子总是与世无争。当三只小动物一再捣乱，他决定策划一场让人捧腹的反击。', 'Sacha Goedegebure · Blender Foundation', 'CC BY 3.0'),
    ('tears-of-steel', '8533ea43-4271-4a57-9694-e9d0b35e1aa1', '钢铁之泪', '科幻', '科幻,真人,原声', 2012,
     '在被机器人占领的未来，一群科学家和战士试图重现过去的关键时刻，修复一段足以改变人类命运的关系。', 'Ian Hubert · Blender Foundation', 'CC BY 3.0'),
    ('elephants-dream', 'cccc3e60-0291-4ecc-aa56-39b2e2c7d0d5', '大象之梦', '奇幻', '奇幻,机械,原声', 2006,
     '两个人走进一座神秘的机械世界。当各自眼中的现实开始分裂，一场有关信任和想象力的冒险悄然展开。', 'Bassam Kurdali · Orange Open Movie Project', 'CC BY 2.5'),
    ('gran-dillama', 'fb70d459-48d2-4db5-adba-813c84f9200a', '羊驼大冒险', '喜剧', '喜剧,冒险,无对白', 2013,
     '绿草就在眼前，羊驼科罗却被一道围栏拦住。为了吃到美味，它使尽浑身解数。原作系列第二部，独立完整故事。', 'Pablo Vazquez · Blender Foundation', 'CC BY 3.0'),
]


def fetch_json(url):
    with urllib.request.urlopen(url, timeout=30) as response:
        return json.load(response)


def sync_one(index_film):
    index, film = index_film
    slug, uid, title, category, tags, year, synopsis, author, license_name = film
    cached = ROOT / '.runtime' / f'{uid}.json'
    data = json.loads(cached.read_text(encoding='utf-8')) if cached.exists() else fetch_json(f'{HOST}/api/v1/videos/{uid}')
    files = data['files']
    primary = min(files, key=lambda item: abs(item['resolution']['id'] - 720))['fileUrl']
    fallback = min(files, key=lambda item: abs(item['resolution']['id'] - 360))['fileUrl']
    # Validate that the source sends video bytes and supports seek requests without authentication.
    for url in {primary, fallback}:
        with urllib.request.urlopen(urllib.request.Request(url, headers={'Range': 'bytes=0-1023'}), timeout=30) as response:
            if response.status != 206 or 'video/' not in response.headers.get('Content-Type', ''):
                raise RuntimeError(f'Invalid playable video: {slug}')
            response.read(1024)
    poster = ROOT / 'frontend' / 'public' / 'posters' / f'{slug}.jpg'
    poster.parent.mkdir(parents=True, exist_ok=True)
    if not poster.exists():
        with urllib.request.urlopen(HOST + data['previewPath'], timeout=30) as response:
            poster.write_bytes(response.read())
    drama = dict(id=slug, title=title, category=category, tags=tags, synopsis=synopsis,
                 poster=f'/posters/{slug}.jpg', backdrop=f'/posters/{slug}.jpg', releaseYear=year,
                 duration=data['duration'], episodeCount=1, sortOrder=index,
                 credit=f'{author} · 原作：{data["name"]}', licenseName=license_name,
                 sourcePage=f'{HOST}/w/{uid}')
    episode = dict(id=f'{slug}-1', dramaId=slug, episodeNumber=1, title=title,
                   duration=data['duration'], videoUrl=primary, fallbackUrl=fallback)
    print(f'Verified {slug}: {data["duration"]} seconds')
    return drama, episode


def main():
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
        entries = list(executor.map(sync_one, enumerate(FILMS)))
    catalog = {'dramas': [item[0] for item in entries], 'episodes': [item[1] for item in entries]}
    target = ROOT / 'backend' / 'src' / 'main' / 'resources' / 'catalog.json'
    target.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
