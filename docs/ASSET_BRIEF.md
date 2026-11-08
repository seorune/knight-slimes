# 에셋 구성

## 리소스 위치

| 경로 | 용도 |
|---|---|
| `assets/art/pixel_knight/` | 기사·근접/원거리 슬라임·복제체·전투 효과·배경 |
| `assets/art/terrain_knight/` | 정측면 지형·벽돌·출구·HUD |
| `assets/art/knight_swing/` | 기사 팔·검의 8프레임 공격 동작 |
| `assets/art/paradox_warden/` | 보스의 6상태 시트 |
| `assets/art/source/` | 캐릭터·효과·배경 입력 PNG |
| `assets/audio/pixel_knight/` | 이벤트 효과음 WAV |

각 팩의 `manifest.json`에 프레임·클립·크기·해시가 정의됩니다. [Asset-Preview.html](../Asset-Preview.html)은 기본 캐릭터·지형·효과음 미리보기입니다.

## 시각·게임 계약

- 기사와 적은 투명 배경의 측면 픽셀 스프라이트입니다. 실제 지형은 정측면 타일이며 장식용 석조 이미지와 구별합니다.
- 1·3스테이지는 아래 warm / 위 cool, 2스테이지는 왼쪽 warm / 오른쪽 cool 팔레트입니다.
- 원본과 복제체는 공통 실루엣을 사용하며 복제체는 보라색입니다. 원본만 처치 시 평평한 벽돌이 되고 복제체는 소멸합니다.
- 전조·활성 공격, 일반 패리·저스트 패리를 서로 구별합니다. 원거리 공격은 이동 탄환이 아니라 수평 직선 즉발 공격입니다.
- 텍스처는 최근접 필터를 사용합니다. 프레임 재생 속도와 충돌 판정은 모델 상태를 따릅니다.

세부 문서: [픽셀 처리](PIXEL_ASSET_PIPELINE.md), [지형](TERRAIN_ASSETS.md), [검 동작](KNIGHT_SWING.md), [보스](BOSS_ART.md), [효과음](AUDIO_PACK.md), [런타임 연결](RUNTIME_ASSETS.md).
