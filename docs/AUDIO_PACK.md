# 효과음

## 파일과 재생

`assets/audio/pixel_knight/`의 WAV는 44,100Hz·모노·signed PCM16 little-endian 형식의 비루프 원샷입니다. 파일 이름과 측정값·SHA-256은 `manifest.json`에 있습니다. 배경 음악이나 앰비언트 루프는 포함하지 않습니다.

[GameAudio.java](../core/src/main/java/io/jbnu/test/GameAudio.java)는 모델 이벤트를 고정 틱 뒤 한 번 소비합니다. 그리기에서 소리를 발생시키지 않으며 M으로 음소거를 전환합니다. `GameAudio.IDS`가 실제 로드 목록입니다.

| 이벤트 | 표현 |
|---|---|
| `jump`, `land_light`, `land_heavy`, `step_stone_01/02` | 이동·착지 |
| `sword_swing`, `melee_push`, `brick_push` | 공격 동작·밀치기 |
| `parry`, `just_parry` | 일반·저스트 패리 |
| `spell_charge`, `spell_line_hit` | 원거리 전조·직선 타격 |
| `clone_spawn`, `clone_disappear`, `resurrect` | 복제·소멸·부활 |
| `time_stop`, `time_resume`, `ui_pause` | 시간·메뉴 |

원본의 벽돌 변환과 복제체 소멸은 서로 다른 이벤트를 사용합니다. 모든 모델 이벤트에 별도 WAV가 있는 것은 아닙니다.

## 합성·검사 명령

프로젝트 루트 기준:

```sh
python tools/generate_sfx.py --self-test
python tools/generate_sfx.py --verify
```

기본 실행 `python tools/generate_sfx.py`는 팩을 생성하거나 덮어씁니다. `--output 경로`로 대상을 지정할 수 있습니다. `--verify`는 기존 WAV 헤더·샘플·피크·RMS·해시를 읽고 검사하며 파일을 변경하지 않습니다. `--self-test`는 임시 출력에서 재현성을 검사합니다.

생성기는 Python 표준 라이브러리로 칩톤·금속 배음·필터 노이즈를 합성합니다. 자동 검사는 디코딩과 수치 계약을 다루며 스피커 음질·주관적 믹싱 평가는 별도입니다. 그래픽 smoke는 음소거로 실행합니다.
