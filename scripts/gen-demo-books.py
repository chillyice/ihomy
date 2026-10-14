#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成演示家庭用的电子书(TXT / EPUB)与封面 SVG(frontend/public/demo/books/)。

书架是「未登录访客可读」的模块之一(`/library/list` 放行、服务层对游客只回 PUBLIC 图书),
但演示家庭原本一本书都没有,游客点进去只见「暂无公开图书」。这里用纯代码产出内容自包含、
无版权问题的演示书:正文为原创短文,封面为矢量插画 —— 全部随 frontend/dist 一起部署。
书里的 file_url 指向 /demo/books/*,不走 /files/ 故无需 FileAccessMapper 反查登记
(与演示照片同一口径,见 docs/踩坑速查.md §7.9)。
需要改文案/换封面时重跑本脚本即可。"""

import os
import zipfile

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "frontend", "public", "demo", "books")
FONT = "system-ui, -apple-system, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif"


# ---------------------------------------------------------------- 封面 SVG(3:4 竖版,与书架卡片的 aspect-ratio 对齐)

def cover(title, author, top, bottom, accent):
    """竖版封面:上下渐变色块 + 柔光圆 + 书名/作者(set slice 自适应裁切)。

    书名里的 `|` 是换行分隔符(单行太长会顶出画面)。"""
    title_lines = title.split("|")
    first_y = 520
    line_h = 112
    last_y = first_y + (len(title_lines) - 1) * line_h
    rects = "".join(
        '<text x="450" y="%d" text-anchor="middle" font-family="%s" font-size="84" font-weight="700" '
        'fill="#FDFBF6">%s</text>' % (first_y + i * line_h, FONT, ln)
        for i, ln in enumerate(title_lines)
    )
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 900 1200" width="900" height="1200" '
        'preserveAspectRatio="xMidYMid slice" role="img" aria-label="%s"><defs>'
        '<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">'
        '<stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/></linearGradient>'
        '<radialGradient id="glow"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.30"/>'
        '<stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></radialGradient></defs>'
        '<rect width="900" height="1200" fill="url(#bg)"/>'
        '<circle cx="450" cy="300" r="300" fill="url(#glow)"/>'
        '<rect x="90" y="90" width="720" height="1020" rx="18" fill="none" stroke="%s" '
        'stroke-opacity="0.55" stroke-width="3"/>'
        '<rect x="380" y="%d" width="140" height="6" rx="3" fill="%s" opacity="0.9"/>'
        '<text x="450" y="%d" text-anchor="middle" font-family="%s" font-size="42" fill="#F3E9DA">%s</text>'
        "%s</svg>"
        % (title.replace("|", ""), top, bottom, accent, last_y + 64, accent, last_y + 158, FONT, author, rects)
    )


# ---------------------------------------------------------------- 正文(xhtml 片段 / 纯文本)

def xhtml(title, body_html):
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="zh-CN" lang="zh-CN">\n'
        "<head><title>%s</title>"
        '<link rel="stylesheet" type="text/css" href="style.css"/></head>\n'
        "<body>\n%s\n</body>\n</html>\n" % (title, body_html)
    )


def chapter(heading, paras):
    h = "<h1>%s</h1>" % heading
    p = "".join("<p>%s</p>" % x for x in paras)
    return xhtml(heading, h + p)


def txt_file(title, author, chapters):
    out = [title, "作者:%s" % author, ""]
    for heading, paras in chapters:
        out.append("=" * 24)
        out.append(heading)
        out.append("=" * 24)
        out.extend(paras)
        out.append("")
    return "\n".join(out).rstrip() + "\n"


# ---------------------------------------------------------------- EPUB 打包

def build_epub(path, title, author, book_id, chapters):
    """极简 EPUB 3(mimetype 首条且 STORED;nav + ncx 双目录;逐章 xhtml)。"""
    manifest, spine, nav_points, nav_lis = [], [], [], []
    for i, (heading, paras) in enumerate(chapters, 1):
        fid = "c%d" % i
        manifest.append('<item id="%s" href="chapter%d.xhtml" media-type="application/xhtml+xml"/>' % (fid, i))
        spine.append('<itemref idref="%s"/>' % fid)
        nav_points.append(
            '<navPoint id="n%d" playOrder="%d"><navLabel><text>%s</text></navLabel>'
            '<content src="chapter%d.xhtml"/></navPoint>' % (i, i, heading, i)
        )
        nav_lis.append('<li><a href="chapter%d.xhtml">%s</a></li>' % (i, heading))

    opf = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="zh-CN">\n'
        '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">\n'
        '<dc:identifier id="bookid">%s</dc:identifier>\n<dc:title>%s</dc:title>\n'
        "<dc:language>zh-CN</dc:language>\n<dc:creator>%s</dc:creator>\n"
        '<meta property="dcterms:modified">2026-10-14T00:00:00Z</meta>\n</metadata>\n'
        "<manifest>\n"
        '<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
        '<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>\n'
        '<item id="css" href="style.css" media-type="text/css"/>\n%s\n</manifest>\n'
        '<spine toc="ncx">\n%s\n</spine>\n</package>\n'
        % (book_id, title, author, "\n".join(manifest), "\n".join(spine))
    )

    nav = xhtml(
        "目录",
        '<nav xmlns:epub="http://www.idpf.org/2007/ops" epub:type="toc" id="toc"><h1>目录</h1><ol>%s</ol></nav>'
        % "".join(nav_lis),
    )

    ncx = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">\n'
        '<head><meta name="dtb:uid" content="%s"/></head>\n'
        "<docTitle><text>%s</text></docTitle>\n<navMap>\n%s\n</navMap>\n</ncx>\n"
        % (book_id, title, "\n".join(nav_points))
    )

    css = (
        "body { font-family: %s; line-height: 1.85; margin: 5%% 7%%; color: #2a2a2a; }\n"
        "h1 { font-size: 1.35em; margin: 1.6em 0 0.8em; }\n"
        "p { text-indent: 2em; margin: 0.7em 0; }\n" % FONT
    )

    container = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">\n'
        '<rootfiles><rootfile full-path="OEBPS/content.opf" '
        'media-type="application/oebps-package+xml"/></rootfiles></container>\n'
    )

    with zipfile.ZipFile(path, "w") as zf:
        # mimetype 必须是压缩包第一条且不压缩,否则部分阅读器直接拒读
        info = zipfile.ZipInfo("mimetype", date_time=(2026, 10, 14, 0, 0, 0))
        info.compress_type = zipfile.ZIP_STORED
        zf.writestr(info, "application/epub+zip")
        zf.writestr("META-INF/container.xml", container, zipfile.ZIP_DEFLATED)
        zf.writestr("OEBPS/content.opf", opf, zipfile.ZIP_DEFLATED)
        zf.writestr("OEBPS/nav.xhtml", nav, zipfile.ZIP_DEFLATED)
        zf.writestr("OEBPS/toc.ncx", ncx, zipfile.ZIP_DEFLATED)
        zf.writestr("OEBPS/style.css", css, zipfile.ZIP_DEFLATED)
        for i, (heading, paras) in enumerate(chapters, 1):
            zf.writestr("OEBPS/chapter%d.xhtml" % i, chapter(heading, paras), zipfile.ZIP_DEFLATED)


# ---------------------------------------------------------------- 演示书内容(原创短文)

FOOD = [
    ("番茄炒蛋", [
        "家里的第一道菜,是我上小学那年跟妈妈学的番茄炒蛋。她说这道菜看着简单,火候全在「蛋要嫩、汁要稠」六个字上。",
        "鸡蛋打散时加一小撮盐,别搅太久,出泡就老了。番茄要去蒂切滚刀块,锅里留底油把番茄炒出沙,再加一小勺白糖提味——糖不是为了甜,是为了让酸味圆润。",
        "最后倒回鸡蛋翻匀,撒一把葱花就能出锅。十分钟,一盘,配白米饭正好。",
    ]),
    ("红烧肉", [
        "红烧肉是我们家周末的仪式感。周五晚上把五花肉切好放冰箱,周六上午十点开工,中午刚好上桌。",
        "肉切麻将块,冷水下锅焯去浮沫。锅里少油,小火炒糖色,看到糖浆泛起琥珀色的细泡就下肉,让每一块都裹上颜色。",
        "加姜片、八角、生抽老抽,再加热水没过肉。小火炖足一小时,最后开大火收汁,汤汁浓稠地挂在肉上,肥而不腻。",
    ]),
    ("皮蛋瘦肉粥", [
        "粥要绵,米要先泡。一杯米洗净,拌一点油和盐,静置半小时,煮的时候米粒才容易开花。",
        "水开下米,转小火熬四十分钟,期间别频繁搅动,米汤自然就稠了。里脊切丝用盐抓过,皮蛋切小块。",
        "起锅前五分钟下肉丝、皮蛋和姜丝,加盐调味,滴几滴香油。盛碗的时候撒葱花,配一根油条,就是一顿舒服的早餐。",
    ]),
]

TRIP = [
    ("海边的黄昏", [
        "下午四点出发,到海边正好赶上退潮。风把沙滩吹出一道道细纹,海水在远处泛着金边。",
        "小宝蹲在沙滩上堆城堡,堆到一半被涨上来的浪冲平,他不哭,反而笑得很响,说「大海把它收走啦」。",
        "天彻底暗下来之前,我们把脚印留在了整片滩上。回去的路上谁都没说话,车里放着很轻的歌。",
    ]),
    ("林间的小路", [
        "森林公园有条两公里的步道,树很密,阳光被筛成一小块一小块地落在地上。",
        "走了半小时,听见了三种鸟叫,还看见一只松鼠抱着一颗果子飞快地窜上树。",
        "这条路适合慢慢走,走快了反而什么都看不见。",
    ]),
    ("湖边看星星", [
        "露营那天特意挑了新月,天黑之后银河真的横在头顶,亮得有点不真实。",
        "湖面把星星又照了一遍,分不清哪里是天哪里是水。",
        "半夜有点凉,加了件外套继续看。认得出北斗七星,剩下的靠手机帮忙数。",
    ]),
]

BEDTIME = [
    ("小熊与月亮", [
        "小熊住在山脚下,每天晚上都要看一眼月亮才肯睡。",
        "有一天月亮不见了,小熊急得团团转,拎着灯笼往山上走。走到半山腰,云散开了,月亮原来只是被云挡住了。",
        "小熊对着月亮挥挥手,说「晚安」。月亮把光洒在它回家的路上,一直送它到门口。",
    ]),
    ("会发光的小石头", [
        "小宝在河边捡到一块灰扑扑的小石头,晚上却发出了淡淡的光。",
        "他把它放在床头,那晚睡得分外香。第二天一早,小石头不见了,枕头边留着一句用露水写的字:「谢谢你陪我」。",
        "小宝把它记在了日记里,他说以后还要去河边,看看能不能再遇见它。",
    ]),
]

PHOTO = [
    ("春·樱花", ["三月底的一场风,把整条路的樱花吹成了雪。快门按下去的那一秒,谁都没舍得眨眼。", "花期很短,一周,但也正因为短,才值得年年都去。"]),
    ("夏·湖畔", ["夏夜的湖面安静得能照出星星。我们在岸边坐到很晚,聊的都是一些不着边际的小事。", "风很凉,虫鸣很近。"]),
    ("秋·公园", ["银杏黄透的那几天,公园里全是举着相机的人。其实站在树下,不拍也好看。", "落叶铺了很厚一层,踩上去沙沙响。"]),
    ("冬·雪后", ["雪停之后的村子安静得像一幅画,屋顶、树枝、田埂都是白的。", "脚印是唯一的颜色。"]),
]

BOOKS = [
    {
        "file": "home-kitchen",
        "title": "家常食记|(演示)",
        "plain": "家常食记",
        "author": "演示家庭 · 厨房",
        "format": "EPUB",
        "desc": "三顿家常饭的随手记录:番茄炒蛋、红烧肉、皮蛋瘦肉粥。做法与火候都写在里面。",
        "chapters": FOOD,
        "cover": ("家常食记", "演示家庭 · 厨房", "#B4553A", "#7C3A2A", "#F0D9B5"),
    },
    {
        "file": "weekend-escape",
        "title": "周末去哪儿|(演示)",
        "plain": "周末去哪儿",
        "author": "演示家庭 · 生活",
        "format": "EPUB",
        "desc": "三次短途出游的随笔:海边的黄昏、林间的小路、湖边看星星。",
        "chapters": TRIP,
        "cover": ("周末去哪儿", "演示家庭 · 生活", "#3F6C7A", "#24424D", "#CFE3E6"),
    },
    {
        "file": "bedtime-stories",
        "title": "给小宝的睡前故事(演示)",
        "plain": "给小宝的睡前故事",
        "author": "演示家庭 · 儿童",
        "format": "TXT",
        "desc": "两个讲给孩子的短故事:小熊与月亮、会发光的小石头。",
        "chapters": BEDTIME,
        "cover": ("给小宝的|睡前故事", "演示家庭 · 儿童", "#6A5AA8", "#3B3163", "#DCD3F2"),
    },
    {
        "file": "seasons-notes",
        "title": "四季风景 · 摄影手记(演示)",
        "plain": "四季风景 · 摄影手记",
        "author": "演示家庭 · 相册",
        "format": "TXT",
        "desc": "跟着相册里的四季走一遍:春樱、夏湖、秋叶、冬雪。",
        "chapters": PHOTO,
        "cover": ("四季风景|摄影手记", "演示家庭 · 相册", "#4C7A5A", "#2C4A38", "#D6E6D2"),
    },
]


def main():
    os.makedirs(OUT, exist_ok=True)
    for b in BOOKS:
        # 封面
        cp = os.path.join(OUT, "cover-" + b["file"] + ".svg")
        svg = cover(*b["cover"])
        with open(cp, "w", encoding="utf-8", newline="\n") as f:
            f.write(svg + "\n")

        # 正文
        if b["format"] == "EPUB":
            bp = os.path.join(OUT, b["file"] + ".epub")
            build_epub(bp, b["plain"], b["author"], "urn:uuid:ihomy-demo-" + b["file"], b["chapters"])
        else:
            bp = os.path.join(OUT, b["file"] + ".txt")
            with open(bp, "w", encoding="utf-8", newline="\n") as f:
                f.write(txt_file(b["plain"], b["author"], b["chapters"]))

        print("wrote", os.path.relpath(cp), os.path.getsize(cp), "bytes")
        print("wrote", os.path.relpath(bp), os.path.getsize(bp), "bytes")


if __name__ == "__main__":
    main()
