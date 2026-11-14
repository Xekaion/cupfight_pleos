package io.autohmg.cupfight;

enum Cosmetic {
    ACTIVE_WING("스펙터 전개 윙", Slot.WING, 85, true,
            "후면의 양쪽 날개가 접혔다 펼쳐지고, 끝을 따라 시안 조명이 켜집니다.", R.drawable.cosmetic_active_wing),
    COMET_TAIL("코멧 테일", Slot.TAIL, 60, true,
            "붉은 후미등을 따라 금빛 불씨가 흘러갑니다.", R.drawable.cosmetic_comet_tail),
    AURORA_GRILLE("오로라 그릴", Slot.GRILLE, 55, true,
            "앞 범퍼의 청록색과 보라색 조명이 번갈아 켜집니다.", R.drawable.cosmetic_aurora_grille),
    PLASMA_WHEELS("플라즈마 휠", Slot.WHEELS, 65, true,
            "휠 테두리의 보랏빛 플라즈마 띠가 회전합니다.", R.drawable.cosmetic_plasma_wheels),
    STARFIELD_COAT("별자리 도장", Slot.BODY, 90, true,
            "짙은 보라색 차체 위의 별빛이 보는 각도에 따라 반짝입니다.", R.drawable.cosmetic_starfield_coat),
    CERAMIC_WHITE("세라믹 펄 화이트", Slot.BODY, 45, false,
            "백색 세라믹 위로 푸른 펄 반사가 번집니다.", R.drawable.cosmetic_ceramic_white),
    PHOTON_DIFFUSER("포톤 디퓨저", Slot.DIFFUSER, 60, true,
            "후면 디퓨저의 핀을 따라 빛이 차례로 흐릅니다.", R.drawable.cosmetic_photon_diffuser),
    STARDUST_TRAIL("스타더스트 잔상", Slot.TRAIL, 70, true,
            "차량 뒤로 금빛과 보랏빛 별 조각이 흩어집니다.", R.drawable.cosmetic_stardust_trail),
    PULSE_UNDERGLOW("펄스 언더글로", Slot.UNDERGLOW, 60, true,
            "차체 아래의 마젠타와 시안 빛이 물결처럼 퍼집니다.", R.drawable.cosmetic_pulse_underglow),
    QUANTUM_ORBIT("퀀텀 오비트", Slot.HOLOGRAM, 95, true,
            "서로 기울기가 다른 두 홀로그램 고리가 차체 주위를 돕니다.", R.drawable.cosmetic_quantum_orbit),
    ION_TURBO("이온 터보", Slot.EXHAUST, 75, true,
            "두 배기구에서 푸른 불꽃이 맥박처럼 뻗습니다.", R.drawable.cosmetic_ion_turbo),
    PRISM_CUP("프리즘 잔", Slot.CUP, 50, true,
            "잔의 가장자리를 따라 무지갯빛 반사가 움직입니다.", 0),
    AURORA_PAINT("오로라 도장", Slot.BODY, 40, true, 0, "푸른빛과 보랏빛이 차체를 따라 흐릅니다."),
    EMBER_PAINT("앰버 도장", Slot.BODY, 45, false, 1, "구리빛 메탈릭 도장"),
    CYBER_WING("사이버 윙", Slot.WING, 55, true, 2, "세 구역의 LED가 차례로 켜집니다."),
    CARBON_DIFFUSER("카본 디퓨저", Slot.DIFFUSER, 35, false, 3, "후면 범퍼 아래 카본 핀"),
    TURBINE_WHEELS("터빈 휠", Slot.WHEELS, 40, true, 4, "터빈 휠 가장자리의 조명이 회전합니다."),
    PULSE_TAIL("펄스 테일", Slot.TAIL, 35, true, 5, "붉은 빛이 후미등을 가로지릅니다."),
    AURORA_UNDERGLOW("오로라 언더글로", Slot.UNDERGLOW, 40, true, 6, "차체 아래로 청록색과 보라색 빛이 번집니다."),
    HOLO_RING("홀로 링", Slot.HOLOGRAM, 60, true, 7, "차량 주변에서 홀로그램 고리가 회전합니다."),
    ION_EXHAUST("이온 배기", Slot.EXHAUST, 55, true, 8, "차량 뒤로 보라색 이온 불꽃이 피어납니다."),
    PIXEL_TRAIL("픽셀 트레일", Slot.TRAIL, 50, true, 9, "차량 뒤에서 푸른 픽셀 조각이 흩어집니다."),
    NEON_WING("네온 윙", Slot.WING, 30, false, "시안 조명이 들어간 후면 윙", R.drawable.neon_wing),
    CRYSTAL_CUP("크리스탈 잔", Slot.CUP, 30, false, "대결 화면에 쓰는 보랏빛 물잔", 0);

    enum Category {
        ALL("전체"), BODY("차체"), PARTS("부품"), EFFECTS("이펙트"), CUP("물잔");

        final String label;

        Category(String label) {
            this.label = label;
        }
    }

    enum Slot {
        BODY("차체", Category.BODY), WING("스포일러", Category.PARTS),
        DIFFUSER("디퓨저", Category.PARTS), WHEELS("휠", Category.PARTS),
        TAIL("후미등", Category.PARTS), GRILLE("전면 그릴", Category.PARTS),
        UNDERGLOW("하부 조명", Category.EFFECTS),
        HOLOGRAM("홀로그램", Category.EFFECTS), EXHAUST("배기 효과", Category.EFFECTS),
        TRAIL("잔상 효과", Category.EFFECTS), CUP("물잔", Category.CUP);

        final String label;
        final Category category;

        Slot(String label, Category category) {
            this.label = label;
            this.category = category;
        }
    }

    final String label;
    final Slot slot;
    final int price;
    final boolean animated;
    final int atlasIndex;
    final int thumbnailResource;
    final String detail;

    Cosmetic(String label, Slot slot, int price, boolean animated, int atlasIndex, String detail) {
        this(label, slot, price, animated, atlasIndex, 0, detail);
    }

    Cosmetic(String label, Slot slot, int price, boolean animated, String detail, int thumbnailResource) {
        this(label, slot, price, animated, -1, thumbnailResource, detail);
    }

    Cosmetic(String label, Slot slot, int price, boolean animated, int atlasIndex,
             int thumbnailResource, String detail) {
        this.label = label;
        this.slot = slot;
        this.price = price;
        this.animated = animated;
        this.atlasIndex = atlasIndex;
        this.thumbnailResource = thumbnailResource;
        this.detail = detail;
    }
}
