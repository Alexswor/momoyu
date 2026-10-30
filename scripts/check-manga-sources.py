"""Check public manga sources and real HLS sample segments without downloading full films."""
import concurrent.futures
import datetime
import json
import urllib.parse
import urllib.request

SOURCES = {
    '360资源': 'https://360zy.com/api.php/provide/vod/?ac=detail&wd=AI',
    '暴风资源': 'https://bfzyapi.com/api.php/provide/vod/?ac=detail&t=68',
}


def read(url, limit=2_000_000, media=False):
    headers = {'User-Agent': 'Mozilla/5.0'}
    if media:
        headers['Range'] = 'bytes=0-2047'
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=20) as response:
        return response.read(limit), response.status, response.headers.get('Access-Control-Allow-Origin', '')


def check_source(job):
    name, api = job
    report = {'source': name, 'api': api}
    try:
        data, status, _ = read(api)
        payload = json.loads(data)
        report.update(apiStatus=status, total=payload.get('total'), samples=[])
        for item in payload.get('list', [])[:2]:
            sample = {'title': item.get('vod_name'), 'remarks': item.get('vod_remarks')}
            report['samples'].append(sample)
            try:
                groups = item.get('vod_play_url', '').split('$$$')
                entries = next(group for group in groups if '.m3u8' in group).split('#')
                manifest = next(entry.split('$', 1)[1] for entry in entries if '.m3u8' in entry)
                content, code, cors = read(manifest)
                sample.update(manifestStatus=code, manifestCors=cors)
                body = content.decode('utf-8-sig')
                if '#EXTM3U' not in body:
                    raise ValueError('Not an HLS playlist')
                lines = [line.strip() for line in body.splitlines() if line.strip() and not line.startswith('#')]
                if '#EXT-X-STREAM-INF' in body:
                    manifest = urllib.parse.urljoin(manifest, lines[0])
                    content, _, cors = read(manifest)
                    body = content.decode('utf-8-sig')
                    lines = [line.strip() for line in body.splitlines() if line.strip() and not line.startswith('#')]
                sample.update(completePlaylist='#EXT-X-ENDLIST' in body, segmentCount=len(lines), playlistCors=cors)
                for label, segment in [('first', lines[0]), ('last', lines[-1])]:
                    blob, code, cors = read(urllib.parse.urljoin(manifest, segment), limit=2048, media=True)
                    sample[label] = {'status': code, 'bytes': len(blob), 'cors': cors}
            except Exception as error:
                sample['error'] = str(error)
    except Exception as error:
        report['error'] = str(error)
    return report


if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        reports = list(pool.map(check_source, SOURCES.items()))
    print(json.dumps({'checkedAt': datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat(), 'sources': reports}, ensure_ascii=True, indent=2))
