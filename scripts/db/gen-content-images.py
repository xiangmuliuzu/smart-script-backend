# -*- coding: utf-8 -*-
"""
B 模块（内容与作品）· 经典数据默认图生成脚本

用途：为 B_20261009_003__seed_content_base.sql 中的作品封面与首页 Banner
      生成后端本地静态图，落库地址为 /profile/upload/content/xxx.png。

前置：
  * 已安装 Pillow（本机 E:\\anaconda 环境自带，import PIL 通过即可）。
  * 后端 ruoyi.profile = D:/ruoyi/uploadPath（见 .env.local），
    故 /profile/upload/content/cover_01.png → <OUT_DIR>/cover_01.png。

产出（OUT_DIR，默认 D:/ruoyi/uploadPath/upload/content）：
  * cover_01.png ~ cover_30.png   480x640  作品封面（含书名 / 题材 / 作者笔名）
  * banner_01.png ~ banner_05.png 750x360  首页 Banner（含标题文案）

用法（PowerShell）：
  & E:\\anaconda\\python.exe scripts\\db\\gen-content-images.py
  自定义输出目录：$env:CONTENT_IMG_DIR='D:/ruoyi/uploadPath/upload/content'; & ...

说明：本脚本只生成本地图片文件，不触碰数据库；幂等，可重复执行。
      书名/笔名/文案必须与 B_20261009_003__seed_content_base.sql 保持一致，
      否则封面文字与库内数据会对不上。
"""

import os
import sys
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = os.environ.get("CONTENT_IMG_DIR", "D:/ruoyi/uploadPath/upload/content")

COVER_SIZE = (480, 640)
BANNER_SIZE = (750, 360)

# 与种子脚本 tmp_author.slot 顺序一致（slot = (idx-1) % 8）
AUTHORS = ["墨雨无痕", "北冥有鱼", "顾清欢", "苏晚棠", "江左沉舟", "云中鹤", "白露未晞", "沈墨白"]

# (idx, 书名, 题材)
WORKS = [
    (1, "九天神王", "玄幻"),
    (2, "焚天纪", "玄幻"),
    (3, "青莲问道", "仙侠"),
    (4, "江湖夜雨录", "武侠"),
    (5, "都市战神归来", "都市"),
    (6, "重生之豪门弃女", "都市"),
    (7, "离婚后我成了首富", "都市"),
    (8, "绝世神医", "都市"),
    (9, "我的老婆是总裁", "都市"),
    (10, "深海迷航", "悬疑"),
    (11, "雾城谜案", "悬疑"),
    (12, "暗河无声", "悬疑"),
    (13, "大明第一书生", "历史"),
    (14, "贞观小吏", "历史"),
    (15, "星际拓荒记", "科幻"),
    (16, "硅基黎明", "科幻"),
    (17, "闪婚老公是大佬", "言情"),
    (18, "总裁的替嫁新娘", "言情"),
    (19, "春日告白", "言情"),
    (20, "余生向暖", "言情"),
    (21, "全服公敌", "游戏"),
    (22, "开局一把木剑", "游戏"),
    (23, "星野高校物语", "二次元"),
    (24, "铁血征程", "军事"),
    (25, "绿茵传奇", "体育"),
    (26, "战神奶爸", "都市"),
    (27, "万古神尊", "玄幻"),
    (28, "重生之嫡女不好惹", "言情"),
    (29, "大佬的隐婚妻子", "都市"),
    (30, "沉默的证人", "悬疑"),
]

# 题材 → (主色, 辅色) 渐变，深色系，贴近男/女频封面观感
GENRE_COLORS = {
    "玄幻": ((38, 24, 72), (92, 46, 145)),
    "仙侠": ((16, 52, 64), (34, 120, 122)),
    "武侠": ((46, 34, 22), (122, 82, 40)),
    "都市": ((22, 36, 66), (46, 96, 156)),
    "悬疑": ((26, 26, 34), (72, 74, 96)),
    "历史": ((52, 36, 24), (120, 86, 46)),
    "科幻": ((14, 34, 58), (30, 104, 168)),
    "言情": ((72, 26, 52), (168, 66, 116)),
    "游戏": ((24, 46, 34), (52, 128, 78)),
    "二次元": ((60, 30, 66), (150, 74, 148)),
    "军事": ((28, 40, 30), (74, 100, 60)),
    "体育": ((26, 46, 40), (40, 124, 96)),
}
DEFAULT_COLORS = ((32, 32, 40), (88, 88, 108))

BANNERS = [
    (1, "都市战神归来", "重磅上线 · 热血逆袭"),
    (2, "九天神王", "新书首发 · 玄幻巨制"),
    (3, "战神奶爸", "短剧热播 · 爽点拉满"),
    (4, "创作激励计划", "限时活动 · 签约有奖"),
    (5, "言情专区", "分类推荐 · 甜宠必看"),
]

FONT_CANDIDATES = [
    "C:/Windows/Fonts/msyhbd.ttc",
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/simhei.ttf",
    "C:/Windows/Fonts/simsun.ttc",
]


def load_font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()


def vgradient(size, top, bottom):
    w, h = size
    img = Image.new("RGB", size, top)
    px = img.load()
    for y in range(h):
        t = y / max(h - 1, 1)
        r = int(top[0] + (bottom[0] - top[0]) * t)
        g = int(top[1] + (bottom[1] - top[1]) * t)
        b = int(top[2] + (bottom[2] - top[2]) * t)
        for x in range(w):
            px[x, y] = (r, g, b)
    return img


def wrap_cjk(text, font, max_width):
    """按字符宽度折行（中文场景）。"""
    lines, cur = [], ""
    for ch in text:
        if font.getlength(cur + ch) <= max_width:
            cur += ch
        else:
            if cur:
                lines.append(cur)
            cur = ch
    if cur:
        lines.append(cur)
    return lines


def draw_center_multiline(draw, lines, font, center_x, start_y, line_gap, fill):
    y = start_y
    for line in lines:
        w = font.getlength(line)
        draw.text((center_x - w / 2, y), line, font=font, fill=fill)
        y += font.size + line_gap
    return y


def make_cover(idx, title, genre, author):
    top, bottom = GENRE_COLORS.get(genre, DEFAULT_COLORS)
    img = vgradient(COVER_SIZE, top, bottom)
    draw = ImageDraw.Draw(img)
    w, h = COVER_SIZE

    # 顶部题材标签（仅描边框，避免 RGB 图上 alpha=0 的 fill 退化为白色实心块）
    tag_font = load_font(24)
    tag_w = tag_font.getlength(genre) + 28
    draw.rounded_rectangle([24, 24, 24 + tag_w, 66], radius=21, outline=(255, 255, 255), width=2)
    draw.text((38, 30), genre, font=tag_font, fill=(255, 255, 255))

    # 书名（大号，折行居中）
    title_font = load_font(58)
    lines = wrap_cjk(title, title_font, w - 96)
    block_h = len(lines) * (title_font.size + 12)
    start_y = (h - block_h) / 2 - 20
    draw_center_multiline(draw, lines, title_font, w / 2, start_y, 12, (255, 255, 255))

    # 分隔线
    draw.line([(w * 0.30, start_y + block_h + 18), (w * 0.70, start_y + block_h + 18)], fill=(255, 255, 255), width=2)

    # 作者笔名
    author_font = load_font(26)
    aw = author_font.getlength("著 / " + author)
    draw.text((w / 2 - aw / 2, start_y + block_h + 42), "著 / " + author, font=author_font, fill=(230, 230, 230))

    # 底部编号（便于与库内 idx 对照）
    small = load_font(20)
    no = "NO.%02d" % idx
    draw.text((w - small.getlength(no) - 24, h - 44), no, font=small, fill=(210, 210, 210))
    return img


def make_banner(idx, main, sub):
    top, bottom = GENRE_COLORS.get("都市", DEFAULT_COLORS)
    img = vgradient(BANNER_SIZE, top, bottom)
    draw = ImageDraw.Draw(img)
    w, h = BANNER_SIZE

    # 右侧装饰圆
    draw.ellipse([w - 220, -80, w + 60, 200], fill=tuple(min(255, c + 24) for c in bottom))
    draw.ellipse([w - 160, h - 120, w + 40, h + 80], outline=(255, 255, 255), width=3)

    title_font = load_font(56)
    sub_font = load_font(26)

    draw.text((56, 96), main, font=title_font, fill=(255, 255, 255))
    # 主标题下划线
    mw = title_font.getlength(main)
    draw.line([(56, 96 + title_font.size + 12), (56 + mw, 96 + title_font.size + 12)], fill=(255, 255, 255), width=3)
    draw.text((56, 96 + title_font.size + 30), sub, font=sub_font, fill=(228, 228, 228))

    small = load_font(20)
    no = "BANNER %02d" % idx
    draw.text((w - small.getlength(no) - 40, h - 44), no, font=small, fill=(210, 210, 210))
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    made = 0
    for idx, title, genre in WORKS:
        author = AUTHORS[(idx - 1) % len(AUTHORS)]
        img = make_cover(idx, title, genre, author)
        path = os.path.join(OUT_DIR, "cover_%02d.png" % idx)
        img.save(path, "PNG")
        made += 1
    for idx, main_title, sub in BANNERS:
        img = make_banner(idx, main_title, sub)
        path = os.path.join(OUT_DIR, "banner_%02d.png" % idx)
        img.save(path, "PNG")
        made += 1
    print("OK generated %d images -> %s" % (made, OUT_DIR))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001
        print("FAIL: %s" % e, file=sys.stderr)
        sys.exit(1)