package com.exem.dust.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 참조 구현과 같은 수열을 내는지 알려진 값으로 확인한다. 생성기의 재현성은 이 수열에 달려 있다. */
class Mt19937Test {

    @Test
    void producesKnownSequenceForProjectSeed() {
        var random = new Mt19937(20230301L);

        assertThat(random.nextDouble()).isEqualTo(0.4302556299353064);
        assertThat(random.nextDouble()).isEqualTo(0.43687220227430723);
        assertThat(random.nextDouble()).isEqualTo(0.7700703456021569);
    }

    @Test
    void uniformScalesTheSameSequence() {
        var random = new Mt19937(20230301L);

        assertThat(random.nextUniform(1 - 0.10, 1 + 0.10)).isEqualTo(0.9860511259870613);
        assertThat(random.nextUniform(1 - 0.10, 1 + 0.10)).isEqualTo(0.9873744404548614);
        assertThat(random.nextUniform(1 - 0.10, 1 + 0.10)).isEqualTo(1.0540140691204314);
    }

    @Test
    void seedIsSplitIntoThirtyTwoBitWordsLowFirst() {
        assertThat(Mt19937.keyWordsOf(20230301L)).containsExactly(20230301);
        assertThat(Mt19937.keyWordsOf(0L)).containsExactly(0);
        assertThat(Mt19937.keyWordsOf(0x1_0000_0002L)).containsExactly(2, 1);
    }

    @Test
    void sameSeedSameSequenceAndDifferentSeedDiffers() {
        var first = new Mt19937(7L);
        var second = new Mt19937(7L);
        var other = new Mt19937(8L);

        for (int i = 0; i < 1000; i++) {
            assertThat(first.nextInt32()).isEqualTo(second.nextInt32());
        }
        assertThat(new Mt19937(7L).nextInt32()).isNotEqualTo(other.nextInt32());
    }
}
