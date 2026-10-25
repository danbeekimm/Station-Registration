package com.exem.dust.domain.alert;

import com.exem.dust.domain.measurement.Pollutant;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 경보 단계별 발령 기준과 등급표. 기준이 바뀌면 이 상수만 고친다.
 * <p>발령 규칙: 시간당 농도가 기준 이상(≥)인 상태가 sustainedHours 이상 이어지면 그 시각에 발령한다.
 * 한 시각에 여러 룰이 충족되면 등급 숫자가 가장 작은(가장 심각한) 룰 하나만 발령한다.
 */
public enum AlertRule {
    PM25_WARNING(1, Pollutant.PM25, 150.0, 2, "초미세먼지 경보"),
    PM10_WARNING(2, Pollutant.PM10, 300.0, 2, "미세먼지 경보"),
    PM25_WATCH(3, Pollutant.PM25, 75.0, 2, "초미세먼지 주의보"),
    PM10_WATCH(4, Pollutant.PM10, 150.0, 2, "미세먼지 주의보"),
    /** 제공 데이터에 오존이 없어 실제로는 발령되지 않지만 기준표의 룰이므로 구현해 둔다. */
    O3_WATCH(5, Pollutant.O3, 0.12, 1, "오존 주의보");

    /** 등급(심각도) 오름차순. 발령 선택은 이 순서로 첫 충족 룰을 고른다. */
    public static final List<AlertRule> BY_SEVERITY = Arrays.stream(values())
            .sorted(Comparator.comparingInt(AlertRule::grade))
            .toList();

    private final int grade;
    private final Pollutant pollutant;
    private final double threshold;
    private final int sustainedHours;
    private final String label;

    AlertRule(int grade, Pollutant pollutant, double threshold, int sustainedHours, String label) {
        this.grade = grade;
        this.pollutant = pollutant;
        this.threshold = threshold;
        this.sustainedHours = sustainedHours;
        this.label = label;
    }

    public boolean isMetBy(Double concentration) {
        return concentration != null && concentration >= threshold;
    }

    /** DB에 저장된 등급 숫자에서 룰을 되찾는다. */
    public static AlertRule ofGrade(int grade) {
        return BY_SEVERITY.stream()
                .filter(rule -> rule.grade == grade)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("정의되지 않은 경보 등급: " + grade));
    }

    public int grade() {
        return grade;
    }

    public Pollutant pollutant() {
        return pollutant;
    }

    public double threshold() {
        return threshold;
    }

    public int sustainedHours() {
        return sustainedHours;
    }

    public String label() {
        return label;
    }
}
