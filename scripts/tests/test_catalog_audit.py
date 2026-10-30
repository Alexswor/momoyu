import importlib.util
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "audit_catalog_completeness.py"
spec = importlib.util.spec_from_file_location("catalog_audit", SCRIPT)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def reference(title, count=100, id="1"):
    return {"id": id, "title": title, "episode_count": count, "category": "AI剧", "url": "https://hongguoduanju.com/detail?series_id=" + id}


def source(title, count=100, id="1"):
    return {"id": id, "title": title, "episode_count": count, "media_entries": 1, "count_basis": "标注集数", "url": "https://bfzyapi.com/api.php/provide/vod/?ac=detail&ids=" + id}


class CatalogAuditTest(unittest.TestCase):
    def test_chinese_and_arabic_season_numbers_match(self):
        first = audit.title_identity("持械入宋：第十季（完）")
        second = audit.title_identity("持械入宋第10季AI短剧")
        self.assertEqual(first["base"], second["base"])
        self.assertEqual(first["season"], 10)
        self.assertEqual(audit.title_identity("烬九州：三十四季")["season"], 34)
        self.assertEqual(audit.title_identity("万妖图录传AI真人版第二季")["base"], audit.title_identity("万妖图录传第二季")["base"])
        self.assertEqual(audit.title_identity("今日事今日完")["base"], "今日事今日完")

    def test_years_sequel_digits_and_quarters_are_not_invented_seasons(self):
        for title in ("七零团宠小福星3", "重生1980", "第一部手机改变命运", "第三季度的奇遇", "四季", "四季与你相遇", "你的四季"):
            self.assertIsNone(audit.title_identity(title)["season"], title)
        self.assertEqual(audit.title_identity("海风奇遇第二部")["season"], 2)

    def test_single_compilation_does_not_become_one_original_episode(self):
        row = {"vod_id": 1, "vod_name": "海风", "vod_play_url": "全集$https://media.example/a.m3u8", "vod_total": 100}
        result = audit.baofeng_record(row)
        self.assertTrue(result["compilation"])
        self.assertEqual(result["episode_count"], 100)
        row["vod_total"] = "未知"
        self.assertIsNone(audit.baofeng_record(row)["episode_count"])

    def test_different_versions_are_never_added_to_fake_a_match(self):
        rows, _ = audit.compare_records([reference("海风奇遇第一季", 105)], [source("海风奇遇第1季", 60), source("海风奇遇第一季AI短剧", 45, "2")])
        self.assertEqual(rows[0]["status"], "同名同季·标注集数偏少")
        self.assertEqual(len(rows[0]["candidates"]), 2)

    def test_same_name_seasons_are_kept_separate(self):
        refs = [reference(f"持械入宋第{season}季", id=str(season)) for season in range(1, 11)]
        sources = [source("持械入宋第一季"), source("持械入宋第二季", id="2"), source("持械入宋", None, "3")]
        rows, families = audit.compare_records(refs, sources)
        self.assertEqual(families[0]["missing_seasons"], list(range(3, 11)))
        self.assertEqual(rows[-1]["status"], "缺少同季条目")
        self.assertTrue(rows[-1]["notes"])

    def test_unlabelled_season_requires_review(self):
        rows, _ = audit.compare_records([reference("海风奇遇第一季")], [source("海风奇遇")])
        self.assertNotEqual(rows[0]["status"], "同名同季·标注集数一致")
        self.assertEqual(rows[0]["candidates"], [])

    def test_html_and_csv_do_not_execute_source_text(self):
        self.assertEqual(audit.csv_safe("=1+1"), "'=1+1")
        with tempfile.TemporaryDirectory() as folder:
            path = pathlib.Path(folder) / "report.html"
            audit.render_html(path, {"title": "</script><img src=x onerror=alert(1)>"})
            text = path.read_text("utf8")
            self.assertNotIn("</script><img", text)
            self.assertIn("\\u003c/script\\u003e", text)


if __name__ == "__main__":
    unittest.main()
