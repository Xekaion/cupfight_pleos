"""Prepare upload text and local video attachments without publishing to YouTube."""
from pathlib import Path
import json
import shutil
from zipfile import ZipFile,ZIP_DEFLATED

ROOT=Path(__file__).resolve().parents[1]
MEDIA=ROOT/'docs/demo-0.9.4'
LOCAL=ROOT/'docs/proposal-2026-11-15/영상_업로드자료'
LOCAL.mkdir(parents=True,exist_ok=True)
HASHES='#HMG차량용앱개발공모전 #PleosAppMarket #CupFight'
COMMON='일부 앱 이미지 제작과 영상 편집·배경음 제작에 AI 도구의 도움을 받았습니다. 배경음은 영상 편집을 위해 추가한 음악이며, 실제 앱 오디오 녹음은 아닙니다.'
NORMAL={
    'file':'cupfight-youtube-demo-0.9.4.mp4',
    'title':'컵파이트 | 현대자동차그룹 차량용 앱 개발 공모전 시연',
    'description':
        '저희 팀 컵스는 정차 중 만난 두 차량이 함께 달리는 구간에서 가상 물잔의 물 잔량을 겨루고, 완주 보상으로 차량 아바타를 꾸미는 컵파이트를 개발하고 있습니다.\n\n'
        '이번 영상에서는 주변 차량과 함께 주행·정차한 뒤 전체 화면 매칭 알림, 양측 수락, 물잔 대결, 정차 후 결과·보상 확인과 상점 미리보기까지 시연했습니다. 마지막 움직이는 날개는 구매·장착이 아닌 미리보기입니다.\n\n'
        '0.9.4 debug 빌드를 일반 Android 에뮬레이터에서 실행해 녹화했습니다. 상대 차량과 주행 데이터는 한 기기의 모의 입력입니다. 실제 이용자 간 온라인 매칭과 최신 공식 Pleos Connect 환경 검증은 후속 개발·검증 범위입니다. 차량 모드에서는 주행 중 화면과 조작을 제한하고 차량 OS의 정책을 따르도록 구성했습니다.\n\n'
        +COMMON+'\n\n소스 코드 및 실행 자료\nhttps://github.com/Xekaion/cupfight_pleos\n\n'+HASHES,
}
SHORT={
    'file':'cupfight-shorts-0.9.4.mp4',
    'title':'컵파이트 | 정차 매칭과 물잔 대결',
    'description':
        '저희 팀 컵스는 정차 중 옆 차와 함께 달리는 구간에서 물잔의 물 잔량을 겨루고, 보상으로 차량 아바타를 꾸미는 컵파이트를 만들고 있습니다.\n\n'
        '이번 영상에서는 매칭 알림부터 양측 수락, 물잔 대결, 정차 후 결과·보상, 움직이는 날개 미리보기까지 보여드립니다.\n\n'
        '일반 Android 에뮬레이터에서 0.9.4 debug 빌드를 실행한 프로토타입 시연입니다. 상대 차량과 주행 데이터는 모의 입력이며, 실제 이용자 간 온라인 연결이나 공식 Pleos 환경 검증 영상은 아닙니다. 차량 모드에서는 차량 OS의 주행 중 화면·조작 제한을 따릅니다.\n\n'
        +COMMON+'\n\nhttps://github.com/Xekaion/cupfight_pleos\n\n'+HASHES,
}
data={'normal':NORMAL,'shorts':SHORT}
(MEDIA/'youtube-upload-texts.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')

def ts(t):
    ms=round(t*1000);sec,ms=divmod(ms,1000);minute,sec=divmod(sec,60);hour,minute=divmod(minute,60)
    return f'{hour:02}:{minute:02}:{sec:02},{ms:03}'

captions=json.loads((MEDIA/'submission-video-info.json').read_text(encoding='utf-8'))['captions']
srt='\n\n'.join(f"{i+1}\n{ts(s['start'])} --> {ts(s['end'])}\n{s['text']}" for i,s in enumerate(captions))+'\n'
(LOCAL/'숏폼_자막.srt').write_text(srt,encoding='utf-8-sig')

parts=[]
for label,item in [('일반 시연 영상',NORMAL),('YouTube Shorts',SHORT)]:
    parts.extend([label,'제목',item['title'],'','설명',item['description'],'','────────────────────────',''])
(LOCAL/'유튜브_제목_설명.txt').write_text('\n'.join(parts),encoding='utf-8-sig')
shutil.copy2(MEDIA/NORMAL['file'],LOCAL/'컵스_컵파이트_시연영상.mp4')
shutil.copy2(MEDIA/SHORT['file'],LOCAL/'컵스_컵파이트_Shorts.mp4')

zip_path=LOCAL.parent/'컵파이트_영상_업로드자료.zip'
with ZipFile(zip_path,'w',ZIP_DEFLATED) as z:
    for p in sorted(LOCAL.iterdir()):
        if p.is_file():z.write(p,p.name)
with ZipFile(zip_path) as z:
    assert z.testzip() is None
    print('Archive files:',', '.join(z.namelist()))
print(zip_path)
