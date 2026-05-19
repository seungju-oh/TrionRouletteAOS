# 🎯 TrionRoulette for Android (트리온 룰렛)
<p align="center">
  <img height="500" alt="5c2d6c38-f6e5-46b5-ba29-62c0a7bea9a9" src="https://github.com/user-attachments/assets/bfa67513-abd7-42a4-92a8-2c6e75132453" />
  <img height="500" alt="8cdc3682-3a59-4eea-bff4-d208af65f29d" src="https://github.com/user-attachments/assets/463f5d1f-767f-429f-b4ec-d99146c993ac" />
</p>

[Appium 자동화](https://github.com/seungju-oh/TrionRoulette-AOS-Appium-Test)를 위해 AI(Gemini 3.1 Pro Standard)를 통해 제작된 타깃 앱입니다.
3가지 모드(기본, 서바이벌, 커스텀 확률)를 지원하며, 사용자 편의성과 QA 테스트 효율성을 극대화한 안드로이드 룰렛 앱입니다.

## ✨ 핵심 기능 (Features)
- **3가지 룰렛 모드 완벽 구현**
  - Flow A (기본): N빵, 랜덤 뽑기 등 가장 기본적인 1/N 룰렛
  - Flow B (서바이벌): 한 번 당첨된 항목은 다음 회차에서 제외되며, 모두 소진 시 자동 초기화되는 서바이벌 모드
  - Flow C (확률): 각 항목의 당첨 확률(%)을 직접 지정하는 고유 확률 모드
- **프리셋 저장 및 불러오기 (SharedPreferences)**
  - 사용자가 입력한 룰렛 리스트를 기기에 영구 저장하여 언제든 재사용 가능 (덮어쓰기 방어 로직 적용)
- **사용자 경험(UX) 최적화**
  - 12가지 색상 팔레트와 중복 배제 알고리즘으로 시각적 구분감 강화
  - 키보드 오버랩 방지 및 애니메이션 스킵 기능 적용

## 🚀 QA 자동화 테스트 대응 (Shift-Left Testing)
Appium 등 UI 자동화 테스트 도구를 활용한 QA 효율성을 높이기 위해, 기획/개발 단계부터 **전체 UI 요소에 Accessibility ID (`contentDescription`)를 체계적으로 매핑**하였습니다.
- 동적 리스트 항목(추가/삭제/수정), 확률 입력창 등 변화하는 인덱스에 맞춘 동적 ID 부여 완료 (`input_item_text_0`, `btn_delete_item_1` 등)
- 팝업창 및 모드 전환 라디오 버튼 등 모든 인터랙션 요소 식별자(ID) 적용 완료

## 🛠 기술 스택 (Tech Stack)
- **Language**: Kotlin
- **UI Toolkit**: Jetpack Compose
- **Local Storage**: SharedPreferences
- **Architecture/Animation**: Canvas API, Animatable (Jetpack Compose)
- **Assist AI**: Google Gemini 3.1 Pro Standard
