import os, sys, subprocess, textwrap
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FR = '/home/claude/kkambbak/ui/build/video-frames/f%04d.png'
FONT = '/home/claude/fonts/pkg/package/dist/public/static/Pretendard-%s.otf'
OUT = '/home/claude/video/out'
os.makedirs(OUT, exist_ok=True)
BG = (13, 15, 18); AMBER = (255, 176, 32); TEXT = (242, 244, 246); TEXT2 = (169, 177, 187); TEXT3 = (110, 119, 129); GOOD = (61, 220, 151); BAD = (255, 107, 91)

def font(w, s): return ImageFont.truetype(FONT % w, s)

def arrows(d, x, y, h, color=AMBER):
    # 깜빡 로고: 바깥을 향한 좌우 화살표 (앱 SignalMark와 같은 비율)
    w = h * 2.1
    def arrow(left):
        tip = x + (w * 0.04 if left else w * 0.96)
        base = x + (w * 0.40 if left else w * 0.60)
        neck = x + (w * 0.22 if left else w * 0.78)
        pts = [(tip, y + h * .5), (neck, y + h * .08), (neck, y + h * .30), (base, y + h * .30), (base, y + h * .70), (neck, y + h * .70), (neck, y + h * .92)]
        d.polygon(pts, fill=color)
    arrow(True); arrow(False)
    return w

def wrap(text, f, maxw, d):
    words = text.split(' ')
    lines, cur = [], ''
    for wd in words:
        t = (cur + ' ' + wd).strip()
        if d.textlength(t, font=f) <= maxw: cur = t
        else:
            if cur: lines.append(cur)
            cur = wd
    if cur: lines.append(cur)
    return lines

def app_frame(n, step, caption, highlight=None):
    """1920x1080: 위 띠(단계), 가운데 앱 화면(1920x880), 아래 자막."""
    im = Image.new('RGB', (1920, 1080), BG)
    d = ImageDraw.Draw(im)
    fr = Image.open(FR % n).convert('RGB')
    im.paste(fr, (0, 80))
    d.rectangle([0, 0, 1920, 80], fill=(18, 21, 26))
    arrows(d, 40, 26, 28)
    d.text((112, 22), '깜빡', font=font('Bold', 30), fill=TEXT)
    d.text((200, 27), step, font=font('Medium', 24), fill=AMBER)
    d.text((1880, 28), '실제 앱 화면 코드로 렌더링 · 주행은 가상 시연 스크립트', font=font('Regular', 20), fill=TEXT3, anchor='ra')
    d.rectangle([0, 960, 1920, 1080], fill=(18, 21, 26))
    d.line([0, 960, 1920, 960], fill=(44, 50, 59), width=2)
    f = font('SemiBold', 38)
    lines = wrap(caption, f, 1760, d)[:2]
    y = 1020 - (len(lines) * 50) // 2
    for ln in lines:
        d.text((960, y), ln, font=f, fill=TEXT if not highlight else highlight, anchor='ma')
        y += 50
    return im

def card(lines, sub=None, foot=None, logo=True):
    im = Image.new('RGB', (1920, 1080), BG)
    d = ImageDraw.Draw(im)
    # 은은한 앰버 빛
    glow = Image.new('RGB', (1920, 1080), BG)
    gd = ImageDraw.Draw(glow)
    gd.ellipse([660, 240, 1260, 840], fill=(70, 48, 10))
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    im = Image.blend(im, glow, 0.9)
    d = ImageDraw.Draw(im)
    y = 330
    if logo:
        w = arrows(d, 960 - 84, y - 120, 80)
    for text, size, color, weight in lines:
        d.text((960, y), text, font=font(weight, size), fill=color, anchor='ma')
        y += int(size * 1.45)
    if sub:
        y += 20
        for s in sub:
            d.text((960, y), s, font=font('Regular', 30), fill=TEXT2, anchor='ma'); y += 46
    if foot:
        d.text((960, 1010), foot, font=font('Regular', 22), fill=TEXT3, anchor='ma')
    return im

segments = []  # (image path, seconds)
def add(img, secs, name):
    p = os.path.join(OUT, name + '.png'); img.save(p); segments.append((p, secs))

# 1. 제목
add(card([('깜빡', 120, TEXT, 'Bold'), ('깜빡했다면, 차가 기억합니다', 48, AMBER, 'SemiBold')],
         sub=['방향지시등 습관을 차가 기록하고, 주차 후 10초로 들려주는 Pleos Connect 앱'],
         foot='현대자동차그룹 차량용 앱 개발 공모전 · 시연 영상'), 4.0, 's00_title')
# 2. 문제
add(card([('운전자 4명 중 1명은 깜빡이를 켜지 않습니다', 56, TEXT, 'Bold')],
         sub=['방향지시등 점등률 75.59% (2024 교통문화지수, 국토교통부·한국교통안전공단)',
              '방향지시등 미사용 공익신고 149만 건 (2023~2025, 경찰청) · 벌점 10점 신설 추진',
              '', '휴대폰은 깜빡이를 볼 수 없습니다. 차는 압니다.'],
         foot='기존 안전운전 점수: 급가속·급감속·심야운행 중심 → 신호 습관은 비어 있음', logo=False), 6.5, 's01_problem')

frames = []
frames.append((1, 2.6, '1 · 주차 상태', '운전을 시작하면 깜빡이 내비 회전 안내마다 방향지시등을 자동으로 기록합니다.'))
cap_drive = '주행 중 화면에는 큰 버튼 두 개뿐입니다. 오른쪽은 심사용 \'시연 관찰 패널\'이에요.'
for n in range(2, 11): frames.append((n, 0.42, '2 · 주행 중 기록', cap_drive))
frames.append((11, 2.0, '2 · 주행 중 기록', '아파트 정문 우회전: 회전 54m 앞에서 켬 → 제때 (기준 30m)'))
for n in range(12, 22): frames.append((n, 0.42, '2 · 주행 중 기록', '내비 회전 안내(NaviHelper)로 남은 거리를 추적하고, 지시등(Vehicle SDK)이 켜진 순간의 거리를 적어요.'))
frames.append((22, 2.0, '2 · 주행 중 기록', '중앙사거리 좌회전: 38m 앞에서 켬 → 제때'))
for n in range(23, 33): frames.append((n, 0.42, '2 · 주행 중 기록', '시청 앞 교차로에 다가가는데, 지시등이 아직 꺼져 있습니다.'))
frames.append((33, 2.6, '2 · 주행 중 기록', '회전 8m 앞에서야 켬 → 늦게. 도로교통법 시행령은 30m(고속도로 100m) 이상 앞에서 신호하도록 정합니다.'))
for n in range(35, 55): frames.append((n, 0.36, '2 · 주행 중 기록', '고속도로 출구는 기준이 100m. 진행방향(FusedLocation)으로 실제로 방향을 틀었는지도 확인해요.'))
frames.append((55, 2.0, '2 · 주행 중 기록', '도심 방면 고속도로 출구: 150m 앞에서 켬 → 제때 (기준 100m)'))
for n in range(56, 67): frames.append((n, 0.36, '2 · 주행 중 기록', '회사 앞 사거리 좌회전으로 다가갑니다.'))
frames.append((67, 2.2, '2 · 주행 중 기록', '회사 앞 사거리 좌회전: 켜지 않음 → 미점등'))
frames.append((77, 6.5, '3 · 주차 후 10초 브리핑', '[음성 브리핑] "회전 6번 중 4번은 제때 켰어요. 늦게 켠 게 1번, 안 켠 게 1번 있었어요. 회사 앞 사거리 좌회전에서 깜빡이가 없었어요."'))
frames.append((78, 5.0, '4 · 주간 리포트', '교통문화지수 전국 점등률(75.6%)과 비교하고, 자주 놓친 곳을 짚어 줍니다.'))
frames.append((79, 5.0, '5 · 깜빡 리워드', '습관을 지키면 내 차 무드등 색(Vehicle SDK Light), 브리핑 목소리, 테마가 열려요.'))
frames.append((80, 4.0, '5 · 깜빡 리워드', '모든 회전을 제때 켠 날은 P단에 놓으면 무드등이 3초간 앰버색으로 인사합니다.'))
for n in range(82, 93): frames.append((n, 0.45, '6 · 사고 순간 기록', '합류 구간, 오른쪽 지시등을 켜고 진로를 바꾸다 앞차와 접촉합니다.'))
frames.append((93, 3.2, '6 · 사고 순간 기록', '급정거를 감지하면 직전 60초 기록을 남길지 먼저 묻습니다. 자동으로 저장하지 않아요.'))
frames.append((94, 3.2, '6 · 사고 순간 기록', '2차 사고 예방 안내를 음성으로 읽어 줍니다(TextToSpeech SDK).'))
for n in (95, 96, 97): frames.append((n, 1.3, '6 · 사고 순간 기록', '기억이 생생할 때 경위를 말로 남깁니다(SpeechToText SDK).'))
frames.append((98, 6.5, '6 · 사고 기록지', '지시등·브레이크·속도 타임라인과 SHA-256 무결성 해시, QR로 휴대폰에. 사실 기록일 뿐, 과실은 판단하지 않습니다.'))
frames.append((81, 4.5, '7 · 데이터 원칙', '판정은 모두 차 안에서. 주행 궤적은 저장하지 않고, 운전자별로 기록을 분리합니다.'))

for i, (n, secs, step, cap) in enumerate(frames):
    add(app_frame(n, step, cap), secs, 'a%03d' % i)

add(card([('깜빡', 110, TEXT, 'Bold'), ('깜빡했다면, 차가 기억합니다', 46, AMBER, 'SemiBold')],
         sub=['Vehicle · NaviHelper · FusedLocation · TextToSpeech · SpeechToText', '공모전 허용 SDK 5종을 모두 핵심 기능에 사용'],
         foot='Kotlin · Jetpack Compose · 판정 엔진 단위 테스트 14개 · 앱 전체 흐름 스모크 테스트'), 5.0, 'z99_end')

# concat 목록
lst = os.path.join(OUT, 'list.txt')
with open(lst, 'w') as f:
    for p, s in segments: f.write("file '%s'\nduration %.3f\n" % (p, s))
    f.write("file '%s'\n" % segments[-1][0])
total = sum(s for _, s in segments)
print('segments', len(segments), 'total %.1fs' % total)
mp4 = '/home/claude/video/kkambbak_demo.mp4'
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', lst,
                '-vf', 'fps=30,format=yuv420p', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20', '-movflags', '+faststart', mp4], check=True)
print(mp4, os.path.getsize(mp4))
