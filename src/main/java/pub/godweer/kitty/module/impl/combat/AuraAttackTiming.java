package pub.godweer.kitty.module.impl.combat;

final class AuraAttackTiming {
    /** Глухой порог: 93% и выше — удар сразу, рандом не играет. В тысячных процента. */
    private static final int INSTANT = 93_000;
    /** Полный заряд удара — 100%. */
    private static final int FULL = 100_000;

    private AuraAttackTiming() {}

    /**
     * charge/threshold — в тысячных процента (91.213% = 91213),
     * charge = round(getAttackStrengthScale * 100000).
     *
     * В воздухе (падение): порог 86.000-93.000% (рандом до тысячных),
     * а при 93% и выше — удар сразу, независимо от рандома.
     * На земле: только полный заряд 100% («Только криты» на земле не бьёт).
     * Криты недоступны (вода, полёт, низкий потолок и пр.) — как в воздухе.
     */
    static boolean canAttack(int charge, int threshold, boolean onGround, boolean onlyCriticals,
                             boolean criticalsUnavailable) {
        if (!onGround || criticalsUnavailable) {
            return charge >= INSTANT || charge >= threshold;
        }
        if (onlyCriticals) return false;
        return charge >= FULL;
    }
}
