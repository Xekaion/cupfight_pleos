"""Reframe the frozen app recording for YouTube and competition Shorts."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import tempfile
import wave
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/demo-0.9.4'
TMP = Path(tempfile.gettempdir()) / 'cupfight-shorts-094'
TMP.mkdir(exist_ok=True)
SOURCE = OUT / 'cupfight-demo-0.9.4.mp4'
FF = Path(r'C:\Users\xekaion\Downloads\LosslessCut-win-x64\resources\ffmpeg.exe')
FONT = Path(r'C:\Windows\Fonts\malgun.ttf')
BOLD = Path(r'C:\Windows\Fonts\malgunbd.ttf')
DURATION = 41.5
shutil.copyfile(FONT, TMP / 'malgun.ttf')

# Times match the observed screen changes in the frozen real app capture.
# crop coordinates refer to the original app surface after removing the video caption bars.
STAGES = [
    (0.0,6.2, '저희 팀은 정차 중 옆 차와\n대결하는 앱을 만들고 있습니다.',
     '함께 달리던 두 차량이 정차합니다', (100,150,800,604)),
    (6.2,8.1, '정차하면 매칭 알림이\n화면 전체에 나타납니다.',
     '정차 중 전체 화면 매칭 알림이 나타납니다', (350,135,580,470)),
    (8.1,11.8, '상대 수락을 확인하고\n저도 수락하면 시작합니다.',
     '상대 수락을 확인한 뒤 수락합니다', (350,135,580,470)),
    (11.8,24.2, '가감속·코너링을\n내 물잔의 움직임으로 표현합니다.',
     '물잔의 움직임으로 대결을 표현합니다', (300,110,680,640)),
    (24.2,31.2, '남은 물로 승부를 가르고\n정차한 뒤 결과를 확인합니다.',
     '정차 후 결과와 완주 보상을 확인합니다', (940,88,320,420)),
    (31.2,33.4, '완주 보상으로\n차량 아바타를 꾸밉니다.',
     '완주 보상으로 차량을 꾸미는 상점을 살펴봅니다', (155,64,970,626)),
    (33.4,41.5, '움직이는 날개를\n내 차량에 미리 적용해 봅니다.',
     '움직이는 날개를 내 차량에 미리 적용합니다', (175,198,580,410)),
]

def font(size,bold=False): return ImageFont.truetype(str(BOLD if bold else FONT),size)

def background(index, caption):
    im=Image.new('RGB',(1080,1920))
    pix=np.zeros((1920,1080,3),dtype=np.uint8)
    for y in range(1920):
        blend=max(0,1-abs(y-670)/1100)
        pix[y,:,:]=[int(7+blend*9),int(14+blend*16),int(24+blend*22)]
    im=Image.fromarray(pix);d=ImageDraw.Draw(im)
    d.text((62,122),'컵파이트',font=font(70,True),fill='#f3f7fd')
    d.text((1006,148),f'{index+1:02d} / 07',font=font(29),fill='#93e8f5',anchor='ra')
    lines=caption.splitlines()
    size=48
    while max(d.textbbox((0,0),s,font=font(size,True))[2] for s in lines)>930:size-=1
    for j,line in enumerate(lines):
        d.text((64,1336+j*77),line,font=font(size,True),fill='#93e8f5' if j==0 else '#f3f7fd')
    for k in range(7):
        x=64+k*136
        d.rounded_rectangle((x,1545,x+117,1551),radius=3,fill='#93e8f5' if k<=index else '#263a4f')
    d.text((64,1613),'일반 Android 에뮬레이터',font=font(28),fill='#b0c4d8')
    d.text((64,1657),'모의 상대 · 모의 주행 입력',font=font(28),fill='#b0c4d8')
    im.save(TMP/f'bg-{index}.png')

def make_music():
    rate=48000;n=int(DURATION*rate);a=np.zeros(n,dtype=np.float64)
    rng=np.random.default_rng(142)
    def add(t,freq,dur,amp,pluck=False):
        start=int(t*rate);count=min(int(dur*rate),n-start)
        if count<=0:return
        x=np.arange(count)/rate
        env=(1-np.exp(-x*12))*np.minimum(1,(dur-x)/.6) if not pluck else np.exp(-x*7)*(1-np.exp(-x*180))
        s=np.sin(2*np.pi*freq*x)+.12*np.sin(2*np.pi*2*freq*x)
        a[start:start+count]+=amp*s*env
    chords=[[220,261.626,329.628],[174.614,220,261.626],[196,261.626,329.628],[196,246.942,293.665]]
    beat=60/100
    for b in range(0,72,8):
        c=chords[(b//8)%4]
        for f in c:add(b*beat,f,5.0,.027)
        add(b*beat,c[0]/2,4.7,.023)
    for k in range(int(DURATION/beat)):
        c=chords[(k//8)%4];add(k*beat,c[[0,2,1,2][k%4]]*2,.65,.035,True)
        start=int(k*beat*rate);count=min(5000,n-start)
        if count>0:
            x=np.arange(count)/rate
            noise=rng.normal(0,1,count);noise=np.concatenate(([0],np.diff(noise)))
            a[start:start+count]+=.003*noise*np.exp(-x*45)
    for t in [6.2,11.8,24.2]:
        add(t,659.255,.6,.032,True);add(t+.13,987.767,.7,.021,True)
    a*=np.minimum(1,np.arange(n)/(rate*.8))*np.minimum(1,(n-np.arange(n))/(rate*1.4))
    a*=.36/max(.36,np.max(np.abs(a)))
    stereo=np.stack((a,.96*a),axis=1)
    with wave.open(str(TMP/'original-score.wav'),'wb') as w:
        w.setnchannels(2);w.setsampwidth(2);w.setframerate(rate)
        w.writeframes((np.clip(stereo,-1,1)*32767).astype('<i2').tobytes())

make_music()
clips=[]
for i,(start,end,caption,long_caption,crop) in enumerate(STAGES):
    background(i,caption)
    x,y,w,h=crop
    factor=min(972/w,980/h)
    sw=round(w*factor/2)*2;sh=round(h*factor/2)*2
    left=(1080-sw)//2;top=round(760-sh/2)
    duration=end-start
    dest=TMP/f'segment-{i}.mp4';clips.append(dest)
    filters=(f'[0:v]crop=1280:754:0:64,crop={w}:{h}:{x}:{y},scale={sw}:{sh}:flags=lanczos,setsar=1[v];'
             f'[1:v][v]overlay={left}:{top}:shortest=1,format=yuv420p[out]')
    subprocess.run([str(FF),'-hide_banner','-loglevel','error','-ss',str(start),'-i',str(SOURCE),
        '-loop','1','-framerate','30','-i',str(TMP/f'bg-{i}.png'),'-filter_complex',filters,
        '-map','[out]','-t',str(duration),'-r','30','-an','-c:v','libx264','-preset','fast',
        '-crf','19','-pix_fmt','yuv420p','-y',str(dest)],check=True)
    print(f'Short scene {i+1}/7 rendered',flush=True)

(TMP/'clips.txt').write_text(''.join("file '"+p.as_posix()+"'\n" for p in clips),encoding='utf-8')
short=OUT/'cupfight-shorts-0.9.4.mp4'
subprocess.run([str(FF),'-v','error','-f','concat','-safe','0','-i',str(TMP/'clips.txt'),
    '-i',str(TMP/'original-score.wav'),'-map','0:v:0','-map','1:a:0','-c:v','copy','-c:a','aac',
    '-b:a','160k','-t',str(DURATION),'-movflags','+faststart','-y',str(short)],check=True)

# A separate horizontal file avoids relying on a repository download as the only video deliverable.
for i,stage in enumerate(STAGES):(TMP/f'normal-{i}.txt').write_text(stage[3],encoding='utf-8')
(TMP/'normal-scope.txt').write_text('0.9.4 · 일반 Android 에뮬레이터 · 모의 상대·주행 입력',encoding='utf-8')
f=['crop=1280:754:0:64','scale=-2:960:flags=lanczos','pad=1920:1080:(ow-iw)/2:0:color=0x0B1420']
for i,(start,end,*_) in enumerate(STAGES):
    f.append(f"drawtext=fontfile=malgun.ttf:textfile=normal-{i}.txt:fontsize=34:fontcolor=0xF3F7FD:x=(w-tw)/2:y=976:enable='gte(t,{start})*lt(t,{end})'")
f.append('drawtext=fontfile=malgun.ttf:textfile=normal-scope.txt:fontsize=23:fontcolor=0xACBED1:x=(w-tw)/2:y=1034')
(TMP/'normal-filter.txt').write_text(','.join(f),encoding='utf-8')
normal=OUT/'cupfight-youtube-demo-0.9.4.mp4'
subprocess.run([str(FF),'-v','error','-i',str(SOURCE),'-i',str(TMP/'original-score.wav'),
    '-filter_script:v',str(TMP/'normal-filter.txt'),'-map','0:v:0','-map','1:a:0',
    '-c:v','libx264','-preset','fast','-crf','19','-pix_fmt','yuv420p','-r','30','-c:a','aac',
    '-b:a','160k','-t',str(DURATION),'-movflags','+faststart','-y',str(normal)],cwd=TMP,check=True)

meta={'app_version':'0.9.4','frozen_app_commit':'dd965d814e098c7d68a814bc5b323ff40a7b04fe',
      'source_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
      'scope':'ordinary Android emulator; mock opponent and driving inputs; no real multiplayer claim',
      'editing':'real capture reframed with captions; no new game UI or gameplay images; playback speed unchanged',
      'audio':'original instrumental score synthesized for this edit; not recorded app audio; no voiceover',
      'shorts':{'file':short.name,'width':1080,'height':1920,'fps':30,'duration':DURATION,
                'sha256':hashlib.sha256(short.read_bytes()).hexdigest()},
      'youtube_demo':{'file':normal.name,'width':1920,'height':1080,'fps':30,'duration':DURATION,
                      'sha256':hashlib.sha256(normal.read_bytes()).hexdigest()},
      'captions':[{'start':a,'end':b,'text':c} for a,b,c,*_ in STAGES]}
(OUT/'submission-video-info.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf-8')
print(short);print(normal)
