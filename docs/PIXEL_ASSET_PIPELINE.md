# 픽셀 에셋 처리

## 입력과 출력

`tools/build_pixel_assets.py`는 Python 표준 라이브러리로 입력 PNG를 읽어 `assets/art/pixel_knight/`의 프레임·시트·manifest를 구성합니다.

입력은 `assets/art/source/`의 `knight_sheet.png`, `melee_slime_sheet.png`, `ranged_slime_sheet.png`, `fx_sheet.png`, `tiles_sheet.png`, `dual_castle_background.png`, `fx_combat_sheet.png`입니다. 원본은 출력 폴더와 분리해 보관합니다.

전투 FX 원본 파일의 출처 URL:
<https://v3b.fal.media/files/b/0ab17b08/BSYrezWYRfuqTvZRtNkym_5dx8ji2O.png>

## PNG·프레임 계약

- 비인터레이스 RGB8/RGBA8 PNG와 필터0–4를 읽고 청크 CRC를 검사합니다. 출력은 RGBA8입니다.
- 초록 매트를 투명 처리하고 최근접 샘플링으로 셀을 정렬합니다. 셀별로 임의 확대하지 않고 그룹 공통 배율을 사용합니다.
- 기본 캐릭터·전투 효과는 64×64 셀입니다. 캐릭터 발 기준은 Y54입니다. 복제체는 원본 실루엣을 보라색으로 바꾸며 벽돌·사망 클립을 제공하지 않습니다.
- manifest의 `rect`는 `[x,y,width,height]`, `alpha_bbox`는 `[left,top,right_exclusive,bottom_exclusive]`입니다. 이미지 좌표는 좌상단 기준입니다.
- 파일별 SHA-256·크기·알파 범위와 그룹별 셀·시트·클립 정보를 manifest에 저장합니다. 런타임 J 동작은 별도 [knight_swing](KNIGHT_SWING.md) 팩을 사용합니다.

## 명령과 주의

```sh
python tools/test_pixel_assets.py
python tools/build_pixel_assets.py
python tools/export_asset_preview.py
python tools/test_asset_preview.py
```

`--root`로 프로젝트 루트를 지정할 수 있습니다. 빌더는 입력을 다시 읽고 출력 폴더의 이전 팩 PNG를 정리할 수 있으므로 직접 편집한 원본을 그 안에 보관하지 마세요. 미리보기 내보내기는 루트 `Asset-Preview.html`을 갱신합니다.

검사는 실제 PNG 디코딩·시트 crop·알파·클립·해시·재현성을 다룹니다. 반복 가능한 지형 채움과 모든 임의 타일 조합의 무봉제성은 서로 다른 조건입니다. 런타임 연결은 [RUNTIME_ASSETS.md](RUNTIME_ASSETS.md)를 참고하세요.
