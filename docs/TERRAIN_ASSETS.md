# 지형·벽돌·출구·HUD

## 구성

`assets/art/terrain_knight/manifest.json`은 정측면 타일과 UI 시트를 정의합니다.

| 그룹 | 셀 | 내용 |
|---|---|---|
| `terrain_warm`, `terrain_cool` | 32×32 | 채움·노출면·벽·모서리·플랫폼·경계 장식 |
| `bricks_warm`, `bricks_cool` | 32×16 | 근접 원형 코어 / 원거리 마름모 코어 벽돌 |
| `exit_warm`, `exit_cool` | 64×96 | 닫힘과 열린 발광 |
| `hud` | 16×16 | 전투·시간·부상·메뉴 아이콘 |

PNG는 RGBA8, 시트는 4열·gutter0입니다. 최근접 필터로 표시합니다. 색상 분할은 스테이지의 축을 따르며 타일 그림이 충돌 사각형을 결정하지는 않습니다.

## 배치 계약

- `stone_fill`은 불투명 내부 채움입니다. `surface_top`, `wall_left/right`, `bottom`과 볼록·오목 모서리는 노출면을 표현합니다.
- `platform`은 32×32 캔버스 상단의 32×8 직사각형입니다. `seam`은 충돌 없는 얇은 장식입니다.
- 벽돌 이미지는 평평한 전폭 상면을 가집니다. 실제 원본 시체의 물리 크기는 모델의 44×22이며 복제체는 벽돌을 남기지 않습니다.
- 출구는 고정 아치 안에서 `closed`와 `open` 발광을 구별합니다. 통과 판정은 스테이지의 `exit` 상자와 `doorUnlocked` 상태를 사용합니다.
- manifest의 `rect`·`collision_rects`는 `[x,y,width,height]`, `alpha_bbox`는 끝 좌표 제외 방식입니다. 이미지 원점은 좌상단입니다. 지형 모델은 Y-up입니다.

반복 채움의 가장자리 일치와 모든 타일 배치의 무봉제성은 다릅니다. 노출 테두리 타일을 내부 채움으로 반복하면 줄무늬가 생길 수 있습니다.

## 명령

```sh
python tools/test_terrain_assets.py
python tools/build_terrain_assets.py
```

빌더는 Python 표준 라이브러리로 타일을 구성하며 입력·출력 해시를 검사해 수정되었거나 소유 목록에 없는 PNG 덮어쓰기를 거부합니다. 검사는 PNG·알파·프레임 좌표·충돌 메타데이터·클립·해시·재현성을 다룹니다. 실제 지형 배치는 각 `Stage*Level.java`에 있습니다.
