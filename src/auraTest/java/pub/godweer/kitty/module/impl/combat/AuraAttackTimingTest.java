package pub.godweer.kitty.module.impl.combat;

public final class AuraAttackTimingTest {
    private static int checks;

    // Аргументы check: имя, ожидание, заряд (в тысячных %), порог (в тысячных %),
    // критыНедоступны, толькоКриты, наЗемле. Пример: 91.213% = 91213.
    public static void main(String[] args) {
        check("в падении порог 86.000 достигнут", true, 86000, 86000, false, false, false);
        check("в падении ниже рандомного порога", false, 85999, 86000, false, false, false);
        check("в падении 91.213 по порогу 91.213", true, 91213, 91213, false, false, false);
        check("в падении 91.212 ниже порога 91.213", false, 91212, 91213, false, false, false);
        check("93% и выше — сразу, даже без рандома", true, 93000, 93000, false, false, false);
        check("94% бьёт мгновенно при любом пороге", true, 94000, 86000, false, false, false);
        check("92.999 ниже обоих порогов — ждёт", false, 92999, 93000, false, false, false);
        check("в падении 92.999 при пороге 86 бьёт", true, 92999, 86000, false, false, false);
        check("в падении без заряда не бьёт", false, 50000, 86000, false, false, false);
        check("на земле 100% — бьёт", true, 100000, 90000, false, false, true);
        check("на земле 99.999% — ждёт", false, 99999, 90000, false, false, true);
        check("на земле 100% бьёт при любом пороге", true, 100000, 93000, false, false, true);
        check("на земле «только криты» — не бьёт", false, 100000, 90000, false, true, true);
        check("криты недоступны: бьёт от порога", true, 86000, 86000, true, true, false);
        check("криты недоступны на земле — от порога", true, 92000, 86000, true, true, true);
        System.out.println("AuraAttackTiming: " + checks + " checks passed");
    }

    private static void check(String name, boolean expected, int charge, int threshold,
                              boolean criticalsUnavailable, boolean onlyCriticals, boolean onGround) {
        boolean actual = AuraAttackTiming.canAttack(charge, threshold, onGround, onlyCriticals, criticalsUnavailable);
        if (actual != expected) {
            throw new AssertionError(name + ": expected " + expected + " got " + actual);
        }
        checks++;
    }
}
