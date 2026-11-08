# 런타임 이미지·효과음

## 연결

[PixelAssets.java](../core/src/main/java/io/jbnu/test/PixelAssets.java)는 `pixel_knight`, `terrain_knight`, `knight_swing`, `paradox_warden` manifest의 시트와 클립을 읽습니다. [StageRenderer.java](../core/src/main/java/io/jbnu/test/StageRenderer.java)가 모델 상태에 따라 그리며 충돌·표적 좌표를 변경하지 않습니다. [GameAudio.java](../core/src/main/java/io/jbnu/test/GameAudio.java)는 고정 틱마다 모델 이벤트를 소비합니다.

- 최근접 필터와 픽셀 정렬을 사용하며 몸통·발 기준으로 스프라이트를 배치합니다. 역중력은 그릴 때 반전합니다.
- 플레이어와 월드 애니메이션 시계는 분리됩니다. 시간 정지는 적·월드 시계를, 일시정지는 전체 시계를 멈춥니다.
- 1·3스테이지는 Y축 상하 팔레트, 2스테이지는 X축 좌우 팔레트입니다. 배경·지형·벽돌·출구가 같은 분할을 따릅니다.

## 상태 표시

상단에는 진행·부상·시간 상태를, 목표에는 하나의 짧은 행동 안내를 표시합니다. 타이틀과 일시정지 화면에서 조작을 확인할 수 있습니다. 전투 결과는 짧은 피드백으로 구별합니다.

1·2스테이지 미완료 표적은 시간 정지 중에만 보이고 해제 후에는 숨습니다. 위치는 유지되며 분신 전달로 출구를 엽니다. 3스테이지의 대기 표적은 해제 후에도 보이고, 새 J 전달로 노출시킨 뒤 새 저스트 패리로 봉인을 소비합니다. 보스의 봉인·노출·충전·공격·파손·패배는 전용 클립으로 구별합니다.

## 검사 명령

프로젝트 루트에서:

```bat
python tools\adapter_contract_test.py
python tools\asset_runtime_test.py
python tools\test_knight_swing_runtime.py
call .tools\project-env.bat
gradlew.bat lwjgl3:classes
python tools\render_smoke.py --scene bossmarkthawed
python tools\render_smoke.py --scene targethidden2
```

렌더 smoke는 음소거된 OpenGL 장면을 제한 프레임 실행하고 `build/`에 PNG를 저장합니다. 지원 장면은 `python tools/render_smoke.py --help`에서 확인합니다. 리소스 로딩·화면 상태 검사는 입력만으로 레벨을 완주하는 모델 경로 검사와 구별합니다. `bossclear` 같은 강제 상태 장면은 메뉴 확인용입니다.
