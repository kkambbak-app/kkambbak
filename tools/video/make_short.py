import os, subprocess
from PIL import Image, ImageDraw, ImageFont, ImageFilter
FR = '/home/claude/kkambbak/ui/build/video-frames/f%04d.png'
FONT = '/home/claude/fonts/pkg/package/dist/public/static/Pretendard-%s.otf'
OUT = '/home/claude/video/short'; os.makedirs(OUT, exist_ok=True)
BG = (13, 15, 18); AMBER = (255, 176, 32); TEXT = (242, 244, 246); TEXT2 = (169, 177, 187); TEXT3 = (110, 119, 129)
W, H = 1080, 1920
def font(w, s): return ImageFont.truetype(FONT % w, s)
def arrows(d, x, y, h, color=AMBER):
    w = h * 2.1
    for left in (True, False):
        tip = x + (w * 0.04 if left else w * 0.96); base = x + (w * 0.40 if left else w * 0.60); neck = x + (w * 0.22 if left else w * 0.78)
        d.polygon([(tip, y + h*.5), (neck, y + h*.08), (neck, y + h*.30), (base, y + h*.30), (base, y + h*.70), (neck, y + h*.70), (neck, y + h*.92)], fill=color)
    return w
def base_canvas(glow=True):
    im = Image.new('RGB', (W, H), BG)
    if glow:
        g = Image.new('RGB', (W, H), BG); gd = ImageDraw.Draw(g); gd.ellipse([140, 560, 940, 1360], fill=(70, 48, 10))
        im = Image.blend(im, g.filter(ImageFilter.GaussianBlur(180)), 0.9)
    return im
def centered(d, y, text, f, fill):
    d.text((W/2, y), text, font=f, fill=fill, anchor='ma')
def text_card(lines, small=None, foot=None, logo=False):
    im = base_canvas(); d = ImageDraw.Draw(im)
    total = sum(int(s*1.3) for _, s, _, _ in lines)
    y = 860 - total // 2
    if logo: arrows(d, W/2 - 105, y - 170, 100)
    for t, s, c, w in lines:
        centered(d, y, t, font(w, s), c); y += int(s * 1.3)
    if small:
        y += 30
        for s in small: centered(d, y, s, font('Regular', 36), TEXT2); y += 52
    if foot: centered(d, 1760, foot, font('Medium', 34), TEXT3)
    return im
def framed(n, crop, top_text, bottom_text=None, top_color=TEXT):
    im = base_canvas(glow=False); d = ImageDraw.Draw(im)
    fr = Image.open(FR % n).convert('RGB').crop(crop)
    scale = (W - 60) / fr.width
    fr = fr.resize((int(fr.width * scale), int(fr.height * scale)), Image.LANCZOS)
    y0 = 960 - fr.height // 2 + 60
    # 둥근 모서리 마스크
    mask = Image.new('L', fr.size, 0); ImageDraw.Draw(mask).rounded_rectangle([0, 0, fr.width, fr.height], 36, fill=255)
    im.paste(fr, (30, y0), mask)
    ImageDraw.Draw(im).rounded_rectangle([30, y0, 30 + fr.width, y0 + fr.height], 36, outline=(44, 50, 59), width=3)
    d = ImageDraw.Draw(im)
    f = font('Bold', 66)
    y = y0 - 80 - 2 * 86
    lines = top_text.split('\n')
    y = y0 - 70 - len(lines) * 86
    for ln in lines: centered(d, y, ln, f, top_color); y += 86
    if bottom_text:
        y = y0 + fr.height + 60
        for ln in bottom_text.split('\n'): centered(d, y, ln, font('SemiBold', 44), TEXT2); y += 62
    arrows(d, 40, 60, 40); d.text((140, 58), '깜빡', font=font('Bold', 42), fill=TEXT)
    return im

segs = []
def add(im, secs, name): p = os.path.join(OUT, name + '.png'); im.save(p); segs.append((p, secs))

add(text_card([('깜빡이,', 120, TEXT, 'Bold'), ('진짜 켜고 계세요?', 120, AMBER, 'Bold')]), 3.0, 's1')
add(text_card([('운전자 4명 중 1명은', 84, TEXT, 'Bold'), ('깜빡이를 안 켭니다', 84, AMBER, 'Bold')],
              small=['방향지시등 점등률 75.59%', '2024 교통문화지수(국토교통부·한국교통안전공단)']), 3.5, 's2')
panel = (960, 0, 1920, 880)
for i, n in enumerate(range(23, 33)):
    add(framed(n, panel, '폰은 깜빡이를 못 봐요.\n차는 봐요.', '내비 회전 안내 + 방향지시등 신호로\n켠 순간의 거리를 잽니다'), 0.4, 'd%02d' % i)
add(framed(33, panel, '회전 8m 앞에서 켬\n→ 늦게', '법정 기준은 30m 이상 앞(고속도로 100m)', AMBER), 3.0, 'late')
add(framed(77, (290, 110, 1075, 760), '주차하면\n10초 음성 브리핑', '"회전 6번 중 4번은 제때 켰어요"'), 3.5, 'brief')
add(framed(78, (150, 120, 1340, 880), '이번 주는\n전국 평균보다 나을까?', '교통문화지수 전국 점등률과 비교'), 3.0, 'weekly')
add(framed(80, (430, 220, 1490, 760), '모든 회전을 제때 켜면', '실내 무드등이 3초간 앰버색으로 인사', AMBER), 3.0, 'celebrate')
add(framed(79, (300, 120, 1920, 880), '습관을 지키면\n차에서 보상이 열려요', '무드등 색 · 브리핑 목소리 · 테마'), 3.0, 'rewards')
add(framed(98, (720, 120, 1920, 880), '사고가 나면\n직전 60초가 증인', '지시등·브레이크·속도 기록 + 무결성 해시'), 3.5, 'crash')
add(text_card([('깜빡', 140, TEXT, 'Bold'), ('깜빡했다면, 차가 기억합니다', 56, AMBER, 'SemiBold')],
              small=['현대자동차그룹 차량용 앱 개발 공모전', 'Pleos Connect 차량용 앱'], foot='#HMG차량용앱개발공모전', logo=True), 3.5, 'end')

lst = os.path.join(OUT, 'list.txt')
with open(lst, 'w') as f:
    for p, s in segs: f.write("file '%s'\nduration %.3f\n" % (p, s))
    f.write("file '%s'\n" % segs[-1][0])
print('total %.1fs' % sum(s for _, s in segs))
mp4 = '/home/claude/video/kkambbak_short.mp4'
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', lst, '-vf', 'fps=30,format=yuv420p',
                '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '21', '-movflags', '+faststart', mp4], check=True)
print(mp4, os.path.getsize(mp4))
