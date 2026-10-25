package com.exem.dust.common.data;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 파싱된 데이터 파일. 제공 파일은 측정소당 1개지만 여러 측정소가 섞인 파일도 다룰 수 있게 코드별로 묶어 둔다.
 *
 * @param warnings 건너뛴 행과 사유. 파서는 로그를 남기지 않고 호출자가 출력 방식을 정한다
 */
public record DustFile(Path source, Map<String, StationData> stations, List<String> warnings) {

    /** 파일에 측정소가 하나뿐일 때 그 측정소. 여러 개면 호출자가 코드를 지정해야 한다. */
    public StationData single() {
        if (stations.size() != 1) {
            throw new DustDataException("%s: 측정소가 %d개 섞여 있습니다. --station-code로 하나를 지정하세요: %s"
                    .formatted(source, stations.size(), stations.keySet()));
        }
        return stations.values().iterator().next();
    }

    /** 지정한 코드의 측정소. 없으면 파일에 있는 코드 목록을 알려 주며 실패한다. */
    public StationData station(String stationCode) {
        var found = stations.get(stationCode);
        if (found == null) {
            throw new DustDataException("%s: 측정소 코드 %s가 없습니다. 파일에 있는 코드: %s"
                    .formatted(source, stationCode, stations.keySet()));
        }
        return found;
    }
}
