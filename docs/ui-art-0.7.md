# 컵파이트 0.7.0 이미지 생성 기록

Codex의 기본 내장 이미지 생성 도구로 원본 6장을 제작했습니다. 세 아이콘 시트는 개별 WebP 리소스로 분리하고 투명 가장자리를 정리했습니다. 네 재질을 버튼의 기본·강조·비활성 상태와 패널에 적용했습니다. 앱에서 사용하는 이미지의 위치는 [drawable-nodpi](../app/src/main/res/drawable-nodpi)입니다.

## 메뉴와 주행 조작 아이콘

```text
Use case: ui-mockup. Asset type: production sprite sheet for the CupFight automotive touchscreen. Generate exactly TWELVE distinct isolated pictograms on a fully transparent background, precisely centered in a regular 4-column by 3-row grid. 4:3 landscape canvas, equally sized square cells, 30 percent clear gutters around every symbol, each symbol entirely inside its own cell. Order top left to right: a SHOPPING BAG with a squared handle, a GARAGE with visible roller-door stripes, a THREE BAR PODIUM (ranking), a SIDE PROFILE SPORT SEDAN. Row 2: a PLAY TRIANGLE, a WATER TUMBLER with a wave, a STEERING WHEEL, an EQUALIZER SLIDERS symbol (simulator). Row 3: a CHECK MARK inside an open outline circle (accept), an X inside an open outline circle (decline), a LEFT CURVING TURN ARROW, a RIGHT CURVING TURN ARROW. Design each symbol with a unique instantly recognizable silhouette. Render polished pale ice-blue satin ceramic and restrained mint edge light, slightly dimensional beveled highlights, dark blue interior voids where needed, front-facing. Clean thick strokes, no strokes thinner than 8 percent of icon width, legible at 24 pixels. This is luxury EV instrument iconography, consistent restrained contrast, zero shadow outside silhouettes. NO text, no labels, no tile borders, no outlines of cells, no backing discs or plates, no gradients across the full image, no visible background, no logos, no checkerboard.
```

## 음량·재생·분류 아이콘

```text
Use case: ui-mockup. Asset type: production utility pictogram atlas for a luxury EV game touchscreen. 4:3 landscape 4 columns x 3 rows, twelve equal square cells, each contains exactly one distinct front-facing glyph centered and separated by at least 30 percent clear gutters. True transparent background. Row one in exact order: SPEAKER WITH TWO WAVES, MUTED SPEAKER WITH SMALL CROSS, PLUS SIGN, MINUS SIGN. Row two: ONE X CLOSE MARK (no circle), PAUSE TWO BARS, CLOCKWISE REPLAY ARROW with small play triangle in the center, LEFT BACK CHEVRON. Row three: FOUR SQUARES IN A 2x2 GRID (all categories), PAINT DROP, SPORT ALLOY WHEEL, SPARKLE (four-point flare and one tiny sparkle). Render clean, thick, front-facing sculpted pale ice-blue satin ceramic and restrained mint highlights. All symbols centered, thick readable silhouettes at 20 pixels, without overly complex detailing. No text, no labels, no grid lines, no plaques or tiles, no external cast shadows, no white field or checkerboard.
```

## 버튼 상태와 패널 재질

```text
Use case: product-mockup. Asset type: production seamless visual material tiles for four Android touchscreen control states, used under live text. A single 2x2 perfectly divided square texture atlas where each equal square fills edge to edge with the texture for its state. No drawn buttons or shapes. Top left: cool luminous ice cyan satin aluminum, readable under dark navy labels, even brightness with smooth vertical highlights. Top right: very dark navy graphite ceramic with slightly blue broad soft top edge highlights, readable under white. Bottom left: muted deep titanium-blue slate with a restrained horizontal sheen, inactive controls. Bottom right: dark slate navy subtle woven carbon used on card surfaces. Four visibly distinct tiles, textures subtle and quiet, no hard highlights, no pattern with large elements, clean luxury automotive console materials, no visible borders between tiles except the abrupt material change at center, no text, no icon, no UI, no watermark. Square aspect ratio.
```

## 매칭 전체 화면 배경

```text
Use case: stylized-concept. Asset type: opaque edge-to-edge background plate behind an automotive game match confirmation screen. Wide cinematic landscape near 16:10. Straight-on symmetrical abstract precision-machined dark graphite dashboard. Two immense barely visible elliptical gauge arcs on the left and right edges, brushed navy metallic sides, dark center negative space across the middle 65 percent for a large match title, statuses and live buttons. Very restrained icy cyan light strips far left and far right, luminous lines toward corners, diffused subtle dawn-like midnight reflection at bottom. Sophisticated near-black navy background without noise or neon clutter. Think modern luxury Korean EV design, premium understated industrial render. All center areas are consistently dark and low contrast from top to bottom. No cockpit controls, no characters, no road, no vehicle, no text, no branding, no symbols, no watermarks, no UI.
```

## 컵파이트 앱 아이콘

```text
Use case: logo-brand. Asset type: a single transparent production app mark and match medallion for CupFight. Center precisely one simple glass tumbler with a wave of vivid clear cyan water inside, mounted into a round precision-milled graphite medallion with thin ice cyan luminous rim. Strong simple cup silhouette, no handles, no tiny details inside glass, restrained polished edge highlights, a dark matte face, realistic tactile materials, perfectly centered front-on, consistent even rim. It must read at 36 pixels, fit cleanly inside a square with 10 percent clear margin all around and have true transparency beyond circular edge, no drop shadow outside. No words, no monogram, no brand symbols, no watermark. Square.
```

## 구매·장착·경로 아이콘

```text
Use case: ui-mockup. Asset type: production action-symbol sprite atlas for a high-end Korean EV cockpit game. Exactly 12 isolated front-view glyphs on a transparent 4:3 canvas, a precise four columns by three rows regular grid of equal square cells, every symbol inside central 62 percent of own square and fully transparent gutter. Exact order: row one, MAP ROUTE with two route points and a simple bent path; SHOPPING CART with two wheels; HEXAGON NUT with a bright center opening; CLOSED PADLOCK. Row two, TROPHY CUP with two side handles; STRAIGHT AHEAD ARROW; SHIELD with checkmark; BOOKMARK RIBBON. Row three, CHECKERED RACING FLAG with a thin pole; CAR PARKING BAY symbol showing a car between two vertical lane marks, absolutely no letter P; TWO STACKED WINDOWS symbol; WATER DROP. Bright pale ice-blue ceramic surfaces with soft cool teal edge highlight, subtle bevels, simple thick uniform geometry and exceptionally crisp silhouettes at 22 pixels. Solid bright bodies, some internal darker cutouts. Consistent icon size and even lighting. No text, lettering, numerals, logos, backing plates, background, tile borders, or shadow beyond each silhouette.
```
