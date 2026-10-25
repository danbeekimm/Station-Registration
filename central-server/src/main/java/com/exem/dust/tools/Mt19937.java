package com.exem.dust.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * MT19937 참조 구현(mt19937ar)과 같은 수열을 내는 난수 생성기. init_by_array 시딩과 53비트 실수 생성(genrand_res53)을 그대로 따른다.
 * java.util.Random 대신 쓰는 이유는 가상 데이터가 어떤 JVM·플랫폼에서도 바이트 단위로 같게 재생성되어야 하기 때문이다.
 * int 연산은 2^32 모듈러이므로 참조 구현의 unsigned 32비트 연산과 결과가 같다.
 */
final class Mt19937 {

    private static final int N = 624;
    private static final int M = 397;
    private static final int MATRIX_A = 0x9908b0df;
    private static final int UPPER_MASK = 0x80000000;
    private static final int LOWER_MASK = 0x7fffffff;

    private final int[] state = new int[N];
    private int index;

    /** 음이 아닌 정수 시드를 32비트 워드(하위부터)로 나눠 init_by_array에 넘긴다. */
    Mt19937(long seed) {
        initByArray(toKey(Math.abs(seed)));
    }

    private static int[] toKey(long seed) {
        if (seed == 0) {
            return new int[]{0};
        }
        var words = new ArrayList<Integer>();
        for (long remaining = seed; remaining != 0; remaining >>>= 32) {
            words.add((int) remaining);
        }
        return words.stream().mapToInt(Integer::intValue).toArray();
    }

    private void initGenrand(int seed) {
        state[0] = seed;
        for (int i = 1; i < N; i++) {
            state[i] = 1812433253 * (state[i - 1] ^ (state[i - 1] >>> 30)) + i;
        }
        index = N;
    }

    private void initByArray(int[] key) {
        initGenrand(19650218);
        int i = 1;
        int j = 0;
        for (int k = Math.max(N, key.length); k > 0; k--) {
            state[i] = (state[i] ^ ((state[i - 1] ^ (state[i - 1] >>> 30)) * 1664525)) + key[j] + j;
            i++;
            j++;
            if (i >= N) {
                state[0] = state[N - 1];
                i = 1;
            }
            if (j >= key.length) {
                j = 0;
            }
        }
        for (int k = N - 1; k > 0; k--) {
            state[i] = (state[i] ^ ((state[i - 1] ^ (state[i - 1] >>> 30)) * 1566083941)) - i;
            i++;
            if (i >= N) {
                state[0] = state[N - 1];
                i = 1;
            }
        }
        state[0] = 0x80000000;
        index = N;
    }

    /** 32비트 난수. 부호 비트까지 쓰므로 unsigned로 해석해야 한다. */
    int nextInt32() {
        if (index >= N) {
            twist();
        }
        int y = state[index++];
        y ^= y >>> 11;
        y ^= (y << 7) & 0x9d2c5680;
        y ^= (y << 15) & 0xefc60000;
        y ^= y >>> 18;
        return y;
    }

    private void twist() {
        for (int kk = 0; kk < N - 1; kk++) {
            int y = (state[kk] & UPPER_MASK) | (state[kk + 1] & LOWER_MASK);
            int source = kk < N - M ? state[kk + M] : state[kk + (M - N)];
            state[kk] = source ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
        }
        int y = (state[N - 1] & UPPER_MASK) | (state[0] & LOWER_MASK);
        state[N - 1] = state[M - 1] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
        index = 0;
    }

    /** [0, 1) 구간의 53비트 정밀도 실수. */
    double nextDouble() {
        long high = Integer.toUnsignedLong(nextInt32()) >>> 5;
        long low = Integer.toUnsignedLong(nextInt32()) >>> 6;
        return (high * 67108864.0 + low) * (1.0 / 9007199254740992.0);
    }

    /** [low, high) 구간의 균등 분포. */
    double nextUniform(double low, double high) {
        return low + (high - low) * nextDouble();
    }

    static List<Integer> keyWordsOf(long seed) {
        var key = toKey(Math.abs(seed));
        var words = new ArrayList<Integer>(key.length);
        for (int word : key) {
            words.add(word);
        }
        return words;
    }
}
