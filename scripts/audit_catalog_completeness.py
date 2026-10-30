"""Compare published Hongguo metadata with the enabled Baofeng AI catalogue.

Only public read-only catalogue requests are made. Playback credentials and video
files are never collected. Raw page caches stay in the ignored .runtime directory.
"""
import argparse
import collections
import concurrent.futures
import csv
import datetime
import difflib
import hashlib
import json
import pathlib
import re
import threading
import time
import unicodedata
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
UTC8 = datetime.timezone(datetime.timedelta(hours=8))
HONGGUO = "https://hongguoduanju.com"
BAOFENG = "https://bfzyapi.com/api.php/provide/vod/"


def timestamp():
    return datetime.datetime.now(UTC8).isoformat(timespec="seconds")


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def positive_int(value):
    text = str(value or "").strip()
    return int(text) if text.isdigit() and int(text) > 0 else None


def read_public(url, cache_dir):
    """One public URL per cache entry; retries remain bounded and unauthenticated."""
    path = cache_dir / (hashlib.sha256(url.encode()).hexdigest() + ".json")
    if path.exists():
        return json.loads(path.read_text("utf-8"))
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", "Accept": "text/html,application/json"})
            with urllib.request.urlopen(req, timeout=24) as response:
                if response.status != 200:
                    raise ValueError(f"HTTP {response.status}")
                body = response.read(4_000_001)
            if len(body) > 4_000_000:
                raise ValueError("Catalogue response exceeds size limit")
            text = body.decode("utf-8-sig")
            if url.startswith(HONGGUO + "/category/"):
                marker = text.find("_ROUTER_DATA")
                if marker < 0:
                    raise ValueError("Public catalogue data is absent")
                start = text.find("{", marker)
                router = json.JSONDecoder().raw_decode(text[start:])[0]
                data = router["loaderData"]["category_$"]
                if not data.get("isSuccess") or not isinstance(data.get("recommendList"), list):
                    raise ValueError("Invalid Hongguo catalogue")
                # Do not retain req/webID, cookies, account data, or unrelated page assets.
                value = {key: data.get(key) for key in ("recommendList", "pagination", "selectorList", "canonicalUrl")}
            else:
                value = json.loads(text)
            item = {"url": url, "fetched_at": timestamp(), "data": value}
            write_json(path, item)
            return item
        except Exception:
            if attempt == 2:
                raise
            time.sleep(0.4 * (attempt + 1))


def catalogue_url(route, page):
    return HONGGUO + route + ("?page=" + str(page) if page != 1 else "")


def baofeng_url(page):
    return BAOFENG + "?" + urllib.parse.urlencode({"ac": "detail", "t": 68, "pg": page, "pagesize": 100, "limit": 100})


def baofeng_record(row):
    groups = []
    for group in str(row.get("vod_play_url", "")).split("$$$"):
        entries = []
        for entry in group.split("#"):
            if "$" not in entry:
                continue
            label, url = entry.split("$", 1)
            if re.fullmatch(r"https?://[^\s]+\.(?:m3u8|mp4)(?:\?[^\s]*)?", url.strip()):
                entries.append((label.strip(), url.strip()))
        groups.append(list(dict.fromkeys(entries)))
    episodes = max(groups, key=len, default=[])
    remarks = str(row.get("vod_remarks", ""))
    compilation = len(episodes) == 1 and ("全集" in remarks or "全集" in episodes[0][0])
    explicit = re.search(r"全?(\d+)集", remarks)
    declared = positive_int(explicit.group(1)) if explicit else positive_int(row.get("vod_total"))
    count = declared or (len(episodes) if len(episodes) > 1 else None)
    return {
        "id": str(row["vod_id"]), "title": str(row.get("vod_name", "")),
        "type": str(row.get("type_name", "")), "episode_count": count,
        "count_basis": "标注集数" if declared else "独立播放条目" if len(episodes) > 1 else "未知",
        "media_entries": len(episodes), "compilation": compilation,
        "remarks": remarks, "updated_at": str(row.get("vod_time", "")),
        "url": BAOFENG + "?ac=detail&ids=" + urllib.parse.quote(str(row["vod_id"])),
    }


def hongguo_record(row, route):
    return {
        "id": str(row["series_id"]), "title": str(row.get("series_name") or row.get("series_title") or ""),
        "episode_count": positive_int(row.get("episode_cnt")) or positive_int((row.get("series_episode_info") or {}).get("episode_cnt")),
        "remarks": str(row.get("episode_right_text", "")),
        "category": "AI剧" if "/ai-drama" in route else "漫剧",
        "routes": [route], "url": HONGGUO + "/detail?series_id=" + str(row["series_id"]),
    }


def collect(cache_dir, expand_topics=True):
    started_at = timestamp()
    cache_dir.mkdir(parents=True, exist_ok=True)
    routes = []
    route_data = {}
    for section in ("ai-drama", "comic-drama"):
        route = "/category/" + section
        first = read_public(catalogue_url(route, 1), cache_dir)
        routes.append(route)
        route_data[route] = first
        if expand_topics:
            for selector in first["data"].get("selectorList") or []:
                for topic in selector.get("items", []):
                    slug = str(topic.get("selector_item_id", ""))
                    if re.fullmatch(r"[a-z-]+", slug):
                        routes.append(route + "/" + slug)
    manifests, bf_pages, hg_pages, errors = [], {}, {}, []
    lock = threading.Lock()

    def baofeng_worker():
        first = read_public(baofeng_url(1), cache_dir)
        data = first["data"]
        if data.get("code") != 1 or not isinstance(data.get("list"), list):
            raise ValueError("Invalid Baofeng response")
        pages = int(data["pagecount"])
        bf_pages[1] = first
        def load(page):
            try:
                result = read_public(baofeng_url(page), cache_dir)
                if result["data"].get("code") != 1 or not isinstance(result["data"].get("list"), list):
                    raise ValueError("Invalid Baofeng page")
                with lock:
                    bf_pages[page] = result
                    if len(bf_pages) % 20 == 0 or len(bf_pages) == pages:
                        print(f"暴风目录：{len(bf_pages)}/{pages}页", flush=True)
            except Exception as error:
                with lock:
                    errors.append({"source": "暴风", "page": page, "error": str(error)})
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(load, range(2, pages + 1)))
        return {"reported_total": int(data["total"]), "expected_pages": pages, "captured_pages": len(bf_pages)}

    def hongguo_worker():
        jobs = []
        for route in routes:
            try:
                first = route_data.get(route) or read_public(catalogue_url(route, 1), cache_dir)
                page_count = int(first["data"]["pagination"]["totalPages"])
                hg_pages[(route, 1)] = first
                manifests.append({"route": route, **first["data"]["pagination"]})
                jobs.extend((route, page) for page in range(2, page_count + 1))
            except Exception as error:
                errors.append({"source": "红果", "route": route, "page": 1, "error": str(error)})
        total = len(jobs) + len(hg_pages)
        def load(job):
            route, page = job
            try:
                value = read_public(catalogue_url(route, page), cache_dir)
                with lock:
                    hg_pages[job] = value
                    if len(hg_pages) % 50 == 0 or len(hg_pages) == total:
                        print(f"红果公开目录：{len(hg_pages)}/{total}页", flush=True)
            except Exception as error:
                with lock:
                    errors.append({"source": "红果", "route": route, "page": page, "error": str(error)})
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(load, jobs))
        return {"routes": manifests, "expected_pages": total, "captured_pages": len(hg_pages)}

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        bf_future, hg_future = pool.submit(baofeng_worker), pool.submit(hongguo_worker)
        bf_coverage, hg_coverage = bf_future.result(), hg_future.result()
    baofeng, hongguo = {}, {}
    for page, value in sorted(bf_pages.items()):
        for row in value["data"]["list"]:
            if "漫剧" in str(row.get("type_name", "")):
                record = baofeng_record(row)
                baofeng[record["id"]] = record
    for (route, page), value in sorted(hg_pages.items()):
        for row in value["data"]["recommendList"]:
            record = hongguo_record(row, route)
            previous = hongguo.get(record["id"])
            if previous:
                previous["routes"] = sorted(set(previous["routes"] + [route]))
                if previous["episode_count"] != record["episode_count"]:
                    previous["metadata_changed_during_capture"] = True
            else:
                hongguo[record["id"]] = record
    snapshot = {
        "started_at": started_at, "finished_at": timestamp(),
        "scope": "红果AI剧、漫剧网页及其公开题材筛选；不代表红果App全库。只核对目录元数据，不验证播放内容。",
        "baofeng_coverage": {**bf_coverage, "unique_records": len(baofeng)},
        "hongguo_coverage": {**hg_coverage, "unique_records": len(hongguo)},
        "errors": errors, "hongguo": list(hongguo.values()), "baofeng": list(baofeng.values()),
    }
    write_json(cache_dir / "snapshot.json", snapshot)
    return snapshot


NUMERALS = {char: value for value, char in enumerate("零一二三四五六七八九")}
NUMERALS.update({"〇": 0, "两": 2})
SEASON = re.compile(r"(?:第\s*)?([零〇一二两三四五六七八九十百千\d]+)\s*季(?!度)|第\s*([零〇一二两三四五六七八九十百千\d]+)\s*部(?=\s*(?:$|[:：之·-]))|\bseason\s*(\d+)\b", re.I)
LABEL = r"(?:AI\s*(?:真人)?(?:漫剧|短剧|剧)(?:版)?|AI\s*真人版|漫剧版|短剧版)"
MARKER = re.compile(r"(?:[（(【\[]\s*(?:" + LABEL + r"|AI|完结|全集|完)\s*[）)】\]]|" + LABEL + r"|\s+AI)\s*$", re.I)


def chinese_number(text):
    if text.isdigit():
        return int(text)
    if not any(unit in text for unit in "十百千"):
        return int("".join(str(NUMERALS[char]) for char in text))
    result, number = 0, 0
    for char in text:
        if char in NUMERALS:
            number = NUMERALS[char]
        else:
            result += (number or 1) * {"十": 10, "百": 100, "千": 1000}[char]
            number = 0
    return result + number


def title_identity(title):
    """Keep unlabelled seasons and trailing sequel digits uncertain, not invented."""
    value = unicodedata.normalize("NFKC", title).strip()
    while True:
        cleaned = MARKER.sub("", value).strip()
        if cleaned == value or not cleaned:
            break
        value = cleaned
    match = SEASON.search(value)
    if match:
        number = next(item for item in match.groups() if item)
        marker = value[match.start():match.end()].lstrip()
        if not marker.startswith("第") and not number.isdigit() and (match.start() == 0 or value[match.start() - 1].isalnum()):
            match = None
    season = chinese_number(next(value for value in match.groups() if value)) if match else None
    base = (value[:match.start()] + value[match.end():]) if match else value
    if not any(char.isalnum() for char in base):
        base, season = value, None
    # Format labels can occur immediately before a season marker as well.
    while True:
        cleaned = MARKER.sub("", base).strip()
        if cleaned == base or not cleaned:
            break
        base = cleaned
    def key(text):
        return "".join(char.lower() for char in text if not char.isspace() and unicodedata.category(char)[0] not in "PZ")
    return {"base": key(base), "season": season, "clean_title": key(value), "display_base": base.strip(" :：-—，,·")}


def supplement_focus(snapshot, titles, cache_dir):
    records = {row["id"]: row for row in snapshot["hongguo"]}
    coverage = snapshot.setdefault("focus_queries", [])
    for title in titles:
        wanted = title_identity(title)["base"]
        endpoint = HONGGUO + "/api/search/result?"
        def fetch(page):
            return read_public(endpoint + urllib.parse.urlencode({"aid": "8662", "query": title, "page": page}), cache_dir)["data"]
        first = fetch(1)
        pages = positive_int(first.get("totalPages")) or 1
        if pages > 100:
            raise ValueError("Focus query is too broad; use an exact series title")
        results, failures = {1: first}, []
        def load(page):
            try:
                return page, fetch(page), None
            except Exception as error:
                return page, None, str(error)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            for page, data, error in pool.map(load, range(2, pages + 1)):
                if error:
                    failures.append({"page": page, "error": error})
                else:
                    results[page] = data
        matched = set()
        for page, data in sorted(results.items()):
            for result in data.get("searchList", []):
                row = result.get("video_data") or {}
                if title_identity(str(row.get("series_title") or row.get("series_name") or ""))["base"] != wanted:
                    continue
                if not re.fullmatch(r"\d+", str(row.get("series_id", ""))):
                    continue
                record = hongguo_record(row, "/category/ai-drama" if str(row.get("genre")) == "205" else "/category/comic-drama")
                record["routes"] = ["/search/" + title]
                matched.add(record["id"])
                if record["id"] in records:
                    records[record["id"]]["routes"] = sorted(set(records[record["id"]]["routes"] + record["routes"]))
                else:
                    records[record["id"]] = record
        coverage[:] = [item for item in coverage if item["query"] != title]
        coverage.append({"query": title, "expected_pages": pages, "captured_pages": len(results), "matched_series_ids": len(matched), "errors": failures})
    snapshot["hongguo"] = list(records.values())
    snapshot["finished_at"] = timestamp()
    snapshot["hongguo_coverage"]["unique_records"] = len(records)
    write_json(cache_dir / "snapshot.json", snapshot)


def compare_records(reference, source):
    by_identity, by_family = collections.defaultdict(list), collections.defaultdict(list)
    display_names, grams = {}, collections.defaultdict(set)
    for row in source:
        identity = title_identity(row["title"])
        record = {**row, **identity}
        by_identity[(identity["base"], identity["season"])].append(record)
        by_family[identity["base"]].append(record)
        display_names[identity["base"]] = identity["display_base"]
    for base in by_family:
        for gram in {base[i:i+2] for i in range(len(base) - 1)}:
            grams[gram].add(base)

    def suggestions(base):
        votes = collections.Counter()
        for gram in {base[i:i+2] for i in range(len(base) - 1)}:
            if len(grams[gram]) <= 150:
                votes.update(grams[gram])
        matches = []
        for candidate, count in votes.most_common(30):
            ratio = difflib.SequenceMatcher(None, base, candidate, autojunk=False).ratio()
            if ratio >= 0.88 and min(len(base), len(candidate)) >= 5:
                matches.append({"title": display_names[candidate], "similarity": round(ratio, 3), "ids": [r["id"] for r in by_family[candidate]]})
        return sorted(matches, key=lambda x: -x["similarity"])[:3]

    rows, families = [], {}
    for original in reference:
        identity = title_identity(original["title"])
        base, season = identity["base"], identity["season"]
        candidates = by_identity.get((base, season), [])
        family_source = by_family.get(base, [])
        fuzzy, notes = [], []
        if candidates:
            playable = [row for row in candidates if row["media_entries"] > 0]
            counts = {row["episode_count"] for row in playable if row["episode_count"]}
            expected = original["episode_count"]
            if not playable:
                status = "同名同季·未返回媒体地址"
            elif not expected or not counts:
                status = "同名同季·集数未知"
            elif expected in counts:
                status = "同名同季·标注集数一致"
            elif max(counts) < expected:
                status = "同名同季·标注集数偏少"
            else:
                status = "同名同季·标注集数不同"
            if len(candidates) > 1:
                notes.append("存在多个版本，保留全部候选，不相加集数")
        elif family_source:
            status = "缺少同季条目" if season is not None else "季数未标·待核验"
            if any(row["season"] is None for row in family_source):
                notes.append("另有未标季条目，需人工核实内容范围")
        else:
            fuzzy = suggestions(base)
            status = "疑似同名·待核验" if fuzzy else "未找到同名条目"
        if original.get("metadata_changed_during_capture"):
            notes.append("抓取期间红果集数曾变化")
        row = {"reference": original, "base": base, "season": season, "status": status,
               "candidates": candidates, "other_seasons": family_source if not candidates else [],
               "suggestions": fuzzy, "notes": notes}
        rows.append(row)
        family_key = original["category"] + ":" + base
        family = families.setdefault(family_key, {"name": identity["display_base"], "category": original["category"],
            "reference_ids": [], "reference_seasons": set(), "source_seasons": {r["season"] for r in family_source if r["season"] is not None and r["media_entries"] > 0},
            "source_unlabelled": any(r["season"] is None for r in family_source), "source_records": len(family_source), "statuses": collections.Counter()})
        family["reference_ids"].append(original["id"])
        if season is not None:
            family["reference_seasons"].add(season)
        family["statuses"][status] += 1
    output_families = []
    for family in families.values():
        family["missing_seasons"] = sorted(family["reference_seasons"] - family["source_seasons"])
        family["reference_seasons"] = sorted(family["reference_seasons"])
        family["source_seasons"] = sorted(family["source_seasons"])
        family["reference_records"] = len(family.pop("reference_ids"))
        family["statuses"] = dict(family["statuses"])
        output_families.append(family)
    output_families.sort(key=lambda row: (row["name"] != "持械入宋", -len(row["missing_seasons"]), row["name"]))
    return rows, output_families


def csv_safe(value):
    text = str(value if value is not None else "")
    return "'" + text if text.lstrip().startswith(("=", "+", "-", "@")) else text


def build_reports(snapshot, output):
    output.mkdir(parents=True, exist_ok=True)
    rows, families = compare_records(snapshot["hongguo"], snapshot["baofeng"])
    summary = {
        "started_at": snapshot["started_at"], "finished_at": snapshot["finished_at"], "scope": snapshot["scope"],
        "baofeng_coverage": snapshot["baofeng_coverage"], "hongguo_coverage": snapshot["hongguo_coverage"],
        "failed_pages": snapshot["errors"], "focus_queries": snapshot.get("focus_queries", []),
        "statuses": dict(collections.Counter(row["status"] for row in rows)),
        "families": len(families), "families_with_missing_season_entries": sum(bool(row["missing_seasons"]) for row in families),
        "duplicate_same_name_season_groups": sum(count > 1 for count in collections.Counter((title_identity(r["title"])["base"], title_identity(r["title"])["season"]) for r in snapshot["baofeng"]).values()),
    }
    write_json(output / "summary.json", summary)
    with (output / "逐项比对.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["红果标题", "分类", "季数", "红果标注集数", "比对结果", "暴风同季候选", "暴风标注集数", "暴风ID", "其他已列季", "疑似同名待复核", "备注", "红果目录链接"])
        for row in rows:
            refs, candidates = row["reference"], row["candidates"]
            writer.writerow(map(csv_safe, [refs["title"], refs["category"], row["season"], refs["episode_count"], row["status"],
                " / ".join(r["title"] for r in candidates), " / ".join(str(r["episode_count"] or "未知") for r in candidates),
                " / ".join(r["id"] for r in candidates), " / ".join(sorted({str(r["season"] or "未标季") for r in row["other_seasons"]})),
                " / ".join(r["title"] for r in row["suggestions"]), "；".join(row["notes"]), refs["url"]]))
    with (output / "系列缺季.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["系列", "分类", "红果目录已见季数", "暴风已列媒体地址季数", "缺少同季条目", "另有未标季记录", "暴风同系列条目数"])
        for row in families:
            writer.writerow(map(csv_safe, [row["name"], row["category"], "、".join(map(str, row["reference_seasons"])),
                "、".join(map(str, row["source_seasons"])), "、".join(map(str, row["missing_seasons"])),
                "是" if row["source_unlabelled"] else "否", row["source_records"]]))
    report_data = {"summary": summary, "families": families, "rows": [
        {"title": row["reference"]["title"], "category": row["reference"]["category"], "season": row["season"],
         "count": row["reference"]["episode_count"], "url": row["reference"]["url"], "status": row["status"],
         "candidates": [{k: r[k] for k in ("title", "episode_count", "id", "url", "media_entries", "count_basis")} for r in row["candidates"]],
         "suggestions": row["suggestions"], "notes": row["notes"]} for row in rows]}
    render_html(output / "比对报告.html", report_data)
    print(json.dumps({"output": str(output), "summary": {k: summary[k] for k in ("statuses", "families", "families_with_missing_season_entries", "duplicate_same_name_season_groups")},
                      "focus": [r for r in families if r["name"] == "持械入宋"]}, ensure_ascii=True), flush=True)


def render_html(path, data):
    serialized = json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
    template = r'''<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>红果与暴风目录比对</title>
<style>
:root{color-scheme:dark;--bg:#10110f;--panel:#191c16;--line:#ffffff18;--accent:#d2f86b;--muted:#a4aa98}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:#f1f3eb;font:15px/1.7 "Microsoft YaHei UI","PingFang SC",sans-serif}main{width:min(1500px,94%);margin:36px auto}h1{font-size:25px;font-weight:600;margin:0 0 12px}h2{font-size:17px}p{margin:7px 0}a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}.muted{color:var(--muted)}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:24px 0}.stat,.note{background:var(--panel);border:1px solid var(--line);border-radius:14px;padding:16px}.stat strong{display:block;color:var(--accent);font-size:27px}.stat span{font-size:13px;color:var(--muted)}.toolbar{display:flex;flex-wrap:wrap;gap:12px;align-items:center;margin:20px 0}button,input,select{font:inherit;color:inherit;border:1px solid var(--line);border-radius:10px;background:#1e221a;padding:9px 13px}button{cursor:pointer}button[aria-pressed="true"]{background:var(--accent);color:#12150a}button:disabled{opacity:.4;cursor:default}input{min-width:230px;flex:1}select{max-width:290px}label{display:flex;align-items:center;gap:8px}.table-wrap{overflow:auto;border:1px solid var(--line);border-radius:14px}table{border-collapse:collapse;width:100%;min-width:950px}th,td{padding:13px;text-align:left;border-bottom:1px solid var(--line);vertical-align:top}th{background:#20251b;font-size:13px;color:#dce8c2;position:sticky;top:0}td{font-size:13px}td:first-child{min-width:220px;font-size:14px}tbody tr:hover{background:#d2f86b06}.tag{display:inline-block;border-radius:7px;background:#d2f86b10;color:var(--accent);padding:2px 7px;white-space:nowrap}.candidate{margin-bottom:8px}.pager{display:flex;justify-content:space-between;align-items:center;margin:16px 0;gap:12px}.downloads{display:flex;gap:20px;flex-wrap:wrap;margin:18px 0}.empty{text-align:center;padding:45px;color:var(--muted)}details{margin:18px 0;color:var(--muted)}.error{color:#ffc887}@media(max-width:700px){main{margin:22px auto}.stats{grid-template-columns:repeat(2,1fr)}h1{font-size:21px}.toolbar input{width:100%}.pager{font-size:12px}}
</style><main>
<h1>红果与暴风目录比对</h1><p class="muted" id="checked"></p>
<div class="note"><p>范围：红果“AI剧”“漫剧”及其公开题材筛选目录，对比暴风“AI漫剧”分类。公开网页有展示上限，结果不代表红果 App 全库。</p><p>这里核对的是剧名、季数和标注集数。“标注集数一致”不等于已经验证整季内容可播；集数差异仍需核实拆分、合并及版本。</p><p id="coverage"></p></div>
<div class="stats"><div class="stat"><strong id="ref-total"></strong><span>红果独立目录条目</span></div><div class="stat"><strong id="src-total"></strong><span>暴风独立目录条目</span></div><div class="stat"><strong id="family-total"></strong><span>缺少同季条目的系列</span></div><div class="stat"><strong id="unmatched-total"></strong><span>未找到严格同名条目</span></div></div>
<div class="downloads"><a href="逐项比对.csv" download>下载逐项比对表</a><a href="系列缺季.csv" download>下载系列缺季表</a><a href="summary.json" download>下载范围与统计</a></div>
<div class="toolbar"><button id="family-tab" aria-pressed="true">系列缺季</button><button id="detail-tab" aria-pressed="false">逐项比对</button><input id="query" aria-label="搜索剧名" placeholder="搜索剧名，例如持械入宋"><select id="category" aria-label="筛选目录类型"><option value="">全部类型</option><option>AI剧</option><option>漫剧</option></select><select id="status" aria-label="筛选比对结果"><option value="">全部结果</option></select></div>
<p class="muted" id="result-count"></p><div class="table-wrap"><table><thead id="thead"></thead><tbody id="tbody"></tbody></table></div>
<div class="pager"><button id="previous">上一页</button><span id="pages"></span><button id="next">下一页</button></div>
<details><summary>比对规则与覆盖范围</summary><p>中文与阿拉伯数字季号统一，忽略标点、空格和末尾 AI 标签。未标季的条目保持“未标季”，尾部续作数字不自动解释为季号。多个版本不相加集数，合集优先读取上游原作集数；单个合集地址不被误记为一集。疑似同名只列候选，未自动合并。</p><pre id="method" style="white-space:pre-wrap"></pre></details>
</main><script type="application/json" id="data">__AUDIT_DATA__</script><script>
const data=JSON.parse(document.getElementById('data').textContent),summary=data.summary,$=id=>document.getElementById(id);let view='family',page=0;const size=50;
$('checked').textContent=`开始：${summary.started_at}　完成：${summary.finished_at}`;
$('ref-total').textContent=summary.hongguo_coverage.unique_records.toLocaleString();$('src-total').textContent=summary.baofeng_coverage.unique_records.toLocaleString();$('family-total').textContent=summary.families_with_missing_season_entries.toLocaleString();$('unmatched-total').textContent=(summary.statuses['未找到同名条目']||0).toLocaleString();
const failed=summary.failed_pages.length+summary.focus_queries.reduce((n,q)=>n+q.errors.length,0);$('coverage').textContent=`暴风读取 ${summary.baofeng_coverage.captured_pages}/${summary.baofeng_coverage.expected_pages} 页；红果读取 ${summary.hongguo_coverage.captured_pages}/${summary.hongguo_coverage.expected_pages} 页，共 ${summary.hongguo_coverage.routes.length} 个分类入口。${failed?'有 '+failed+' 个页面读取失败，结果存在缺口。':'本次计划页面均已读取。'}`;if(failed)$('coverage').className='error';
$('method').textContent=JSON.stringify({分类范围:summary.hongguo_coverage.routes,重点系列补查:summary.focus_queries,各项结果:summary.statuses},null,2);
Object.keys(summary.statuses).sort().forEach(value=>{$('status').add(new Option(value,value))});
function cell(row,text,tag=false){const td=document.createElement('td');if(tag){const span=document.createElement('span');span.className='tag';span.textContent=text;td.append(span)}else td.textContent=text;row.append(td);return td}
function link(parent,title,url){const a=document.createElement('a');a.textContent=title;a.href=url;a.target='_blank';a.rel='noreferrer';parent.append(a)}
function seasons(values){return values.length?values.join('、'):'未标季'}
function render(){const query=$('query').value.trim().toLowerCase(),category=$('category').value,status=$('status').value;let values=view==='family'?data.families:data.rows;values=values.filter(row=>(!category||row.category===category)&&(!query||String(row.name||row.title||'').toLowerCase().includes(query))&&(!status||(view==='family'?row.statuses[status]:row.status===status)));const pageCount=Math.max(1,Math.ceil(values.length/size));page=Math.min(page,pageCount-1);$('family-tab').setAttribute('aria-pressed',view==='family');$('detail-tab').setAttribute('aria-pressed',view==='detail');$('result-count').textContent=`共 ${values.length.toLocaleString()} 项，每页 ${size} 项`;
const headings=view==='family'?['系列','类型','红果目录已见季数','暴风已列季数','缺少同季条目','未标季版本','暴风条目数']:['红果标题','类型','季数','红果标注集数','比对结果','暴风同名同季候选','待复核说明'];$('thead').replaceChildren();const tr=document.createElement('tr');headings.forEach(text=>{const th=document.createElement('th');th.textContent=text;tr.append(th)});$('thead').append(tr);$('tbody').replaceChildren();
values.slice(page*size,(page+1)*size).forEach(row=>{const tr=document.createElement('tr');if(view==='family'){const name=cell(tr,row.name);if(row.missing_seasons.length){const b=document.createElement('div');b.className='muted';b.textContent='缺少同季目录条目';name.append(b)}cell(tr,row.category);cell(tr,seasons(row.reference_seasons));cell(tr,row.source_seasons.length?row.source_seasons.join('、'):'未找到标季条目');cell(tr,row.missing_seasons.length?row.missing_seasons.join('、'):'无',!!row.missing_seasons.length);cell(tr,row.source_unlabelled?'另有，需核验':'无');cell(tr,row.source_records)}else{link(cell(tr,''),row.title,row.url);cell(tr,row.category);cell(tr,row.season??'未标季');cell(tr,row.count??'未知');cell(tr,row.status,true);const list=cell(tr,'');if(!row.candidates.length)list.textContent='无严格同名同季候选';row.candidates.forEach(c=>{const item=document.createElement('div');item.className='candidate';link(item,c.title,c.url);const text=document.createElement('div');text.className='muted';text.textContent=`${c.episode_count??'未知'} 集 · ${c.media_entries} 个媒体地址 · ${c.count_basis}`;item.append(text);list.append(item)});cell(tr,[...row.notes,...row.suggestions.map(s=>'疑似：'+s.title+'，相似度 '+s.similarity)].join('；'))}$('tbody').append(tr)});if(!values.length){const tr=document.createElement('tr'),td=cell(tr,'没有符合筛选条件的记录');td.colSpan=headings.length;td.className='empty';$('tbody').append(tr)}$('pages').textContent=`第 ${page+1} / ${pageCount} 页`;$('previous').disabled=page===0;$('next').disabled=page>=pageCount-1;}
$('query').addEventListener('input',()=>{page=0;render()});['category','status'].forEach(id=>$(id).addEventListener('change',()=>{page=0;render()}));$('family-tab').onclick=()=>{view='family';page=0;render()};$('detail-tab').onclick=()=>{view='detail';page=0;render()};$('previous').onclick=()=>{page--;render()};$('next').onclick=()=>{page++;render()};render();
</script></html>'''
    path.write_text(template.replace("__AUDIT_DATA__", serialized), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    day = datetime.datetime.now(UTC8).strftime("%Y%m%d")
    parser.add_argument("--cache", type=pathlib.Path, default=ROOT / ".runtime" / ("catalog-audit-" + day))
    parser.add_argument("--collect-only", action="store_true")
    parser.add_argument("--cached-only", action="store_true")
    parser.add_argument("--base-categories-only", action="store_true")
    parser.add_argument("--focus", action="append", default=[], help="Exact series name to supplement through public search")
    parser.add_argument("--output", type=pathlib.Path, default=ROOT / "reports" / ("catalog-audit-" + day))
    args = parser.parse_args()
    snapshot = json.loads((args.cache / "snapshot.json").read_text("utf-8")) if args.cached_only else collect(args.cache, not args.base_categories_only)
    if args.focus:
        supplement_focus(snapshot, args.focus, args.cache)
    print(json.dumps({"baofeng": snapshot["baofeng_coverage"], "hongguo": {k: v for k, v in snapshot["hongguo_coverage"].items() if k != "routes"}, "failed_pages": len(snapshot["errors"])}, ensure_ascii=True), flush=True)
    if not args.collect_only:
        build_reports(snapshot, args.output)


if __name__ == "__main__":
    main()
