# 기사 검 공격 동작

## 리소스

`assets/art/knight_swing/manifest.json`의 `knight_swing` 그룹은 팔·손·검 자체가 움직이는 캐릭터 동작입니다. 별도의 회전 슬래시 효과가 아닙니다.

- 개별 프레임: `frame_00.png`–`frame_07.png`, 96×96 RGBA8.
- 시트: `sheet.png`, 384×192, 4열·2행, 행 우선.
- 고정 기준: `root_x=42`, `baseline=70`.
- `melee` 클립: 8프레임, 프레임당 0.035초, 총 0.28초, 비루프.
- 기본 몸체 입력: `assets/art/pixel_knight/knight/frame_00.png`.

## 모델과 정렬

동작은 들어 올리기 → 내려베기 → 회수 순서입니다. 프레임2의 시작인 0.07초 이후 첫 고정 틱에서 J 넉백을 한 번 적용합니다. 시각 효과가 추가 공격 판정을 만들지 않습니다.

몸통·머리·발·방패의 기준을 유지하고 팔과 검을 관절 좌표로 배치합니다. 낮은 내려베기에서는 손·손잡이 연결이 보이도록 기존 방패 내부와 전경 레이어 순서를 조정합니다. 역중력과 좌우 반전은 런타임 그리기에서 처리하며 공유 텍스처 좌표를 변경하지 않습니다.

## 명령

```sh
python tools/build_knight_swing.py
python tools/test_knight_swing.py
python tools/test_knight_swing_runtime.py
```

검사는 프레임별 관절·칼날 연결·고정된 몸체/발 기준·투명도·시트 crop·해시를 다룹니다. 실제 타격 시점은 [StageModel.java](../core/src/main/java/io/jbnu/test/StageModel.java)의 공격 타이머가 결정합니다.
