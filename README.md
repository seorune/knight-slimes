# KNIGHT / SLIMES

시간 정지, 분신 발판, 중력 전환과 저스트 패리를 사용하는 LibGDX 데스크톱 퍼즐 플랫포머입니다.

## 실행

[실행 JAR 다운로드 — v1.0.0](https://github.com/seorune/knight-slimes/releases/tag/v1.0.0) · 로그인 없이 다운로드할 수 있습니다. 다운로드한 JAR은 Java 17에서 `java -jar Project2-1.0.0.jar`로 실행합니다.

Windows에서 `Play-Stage1.bat`를 실행합니다. `Play-Stage2.bat`, `Play-Stage3.bat`는 해당 스테이지부터 시작합니다. 실행기는 프로젝트의 `.tools` 설정이 있으면 사용하고, 없으면 설치된 JDK 17을 사용합니다. 첫 의존성 다운로드에는 인터넷이 필요할 수 있습니다.

명령 프롬프트에서 프로젝트 루트 기준:

```bat
gradlew.bat lwjgl3:run
```

Java 8 호환 소스, 로컬 JDK 17, Gradle Wrapper 8.14.3, LibGDX 1.13.1을 사용합니다. 기본 화면은 1280×720이며 다른 비율에서는 레터박스가 적용됩니다.

## 조작

| 키 | 동작 |
|---|---|
| Enter | 시작 / 클리어 후 다음 스테이지 |
| A/D, ←/→ | 이동 |
| Space, W, ↑ | 중력 반대 방향 점프 |
| J | 근접 밀치기: 피해·처치 없음 |
| K | 누르는 동안 방어, 최초 0.08초는 저스트 패리 |
| L | 3초 시간 정지, 종료 후 4초 쿨다운 |
| M | 효과음 음소거 전환 |
| Esc | 일시정지 / 재개 |
| R | 일시정지·사망·클리어 메뉴에서 현재 스테이지 재시작 |
| N | 최종 승리 화면에서 1스테이지부터 새 게임 |

첫 피격 후 5초 동안 부상 상태이며 그 안에 다시 유효 타격을 받으면 사망합니다. 저스트 패리는 부상을 해제하고 짧은 무적을 줍니다. 시간 정지 중에는 적 밀치기·패리·처치가 불가능하지만 이미 활성화된 공격은 위험합니다. 실제 착지 없이 포털을 연속 두 번 통과하면 사망합니다.

## 세 스테이지

- **[1 — Boundary Foundry](docs/STAGE1_MAP.md)**: 가로 진행, 상하 순환과 중력 반전. L로 표적 위치를 기억한 뒤 정지 해제 후 살아 있는 분신을 전달해 출구를 엽니다. 분신과 벽돌을 발판으로 사용합니다.
- **[2 — Lateral Ascent](docs/STAGE2_MAP.md)**: 세로 탑, 좌우 순환. 동쪽 표적에 정지 해제 후 살아 있는 분신을 전달해 출구를 열고 정상으로 오릅니다. 1·2스테이지 표적은 시간 정지 중에만 보입니다.
- **[3 — Paradox Warden](docs/STAGE3_BOSS.md)**: 사방 순환 보스방. L 표적은 해제 후에도 보입니다. 해제 뒤 새 J로 살아 있는 분신을 표적에 밀어 노출시키고, 새 저스트 패리로 봉인을 하나씩 깨뜨립니다. 세 봉인을 모두 깨면 승리합니다.

## 검사

프로젝트 루트에서 실행합니다. Python 모델 검사는 로컬 JDK로 순수 Java 모델과 테스트를 컴파일하며 JUnit은 필요하지 않습니다.

```bat
python tools\model_test.py
python tools\adapter_contract_test.py
python tools\asset_runtime_test.py
gradlew.bat core:check lwjgl3:classes
python tools\render_smoke.py --scene bossmarkthawed
```

`core:check`는 규칙·보스 경로·표적·연속 캠페인 검사를 실행합니다. 그래픽 smoke는 음소거된 제한 프레임 장면을 캡처합니다. 장면 fixture는 실제 레벨 완주나 수동 난이도 평가를 대신하지 않습니다.

## 코드와 자료

- [개발 레포트](docs/REPORT.md): 과제 요구사항·구현·검증·제출 현황
- [노션 레포트](https://app.notion.com/p/KNIGHT-SLIMES-4125efcaaaea8160b377e546c9dbf821)
- [에셋 출처와 사용 범위](docs/ASSET_SOURCES.md)
- [발표 초안 PPTX](docs/presentation/KNIGHT_SLIMES_발표초안.pptx) · [PDF](docs/presentation/KNIGHT_SLIMES_발표초안.pdf) · [발표 대본](docs/presentation/발표대본.md)

- [StageModel.java](core/src/main/java/io/jbnu/test/StageModel.java): 120Hz 결정적 물리·전투·표적·진행 규칙
- [StageOneLevel.java](core/src/main/java/io/jbnu/test/StageOneLevel.java), [StageTwoLevel.java](core/src/main/java/io/jbnu/test/StageTwoLevel.java), [StageThreeLevel.java](core/src/main/java/io/jbnu/test/StageThreeLevel.java): 지도
- [StageRenderer.java](core/src/main/java/io/jbnu/test/StageRenderer.java): 입력·화면·카메라·메뉴
- [PixelAssets.java](core/src/main/java/io/jbnu/test/PixelAssets.java), [GameAudio.java](core/src/main/java/io/jbnu/test/GameAudio.java): 이미지·효과음
- [규칙](docs/STAGE1_RULES.md), [런타임 에셋](docs/RUNTIME_ASSETS.md), [에셋 목록](docs/ASSET_BRIEF.md), [미리보기](Asset-Preview.html)
- 테스트: `core/src/test/java/io/jbnu/test/` / 실행 도구: `tools/`

진행 저장·체크포인트·키 재설정은 지원하지 않습니다.
