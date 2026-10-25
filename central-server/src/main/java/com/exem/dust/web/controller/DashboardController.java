package com.exem.dust.web.controller;

import com.exem.dust.dao.DashboardDao;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.web.dto.StationCard;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * GET / — 단일 페이지 대시보드. 측정소 카드는 station 테이블 행 수만큼 만들어지고, 이후 갱신은 SSE가 맡는다.
 * 차트 초기 데이터는 서버에서 JSON으로 직렬화해 스크립트에 그대로 심는다.
 */
@Controller
public class DashboardController {

    static final int CHART_POINTS = 48;
    static final int ALERT_FEED_SIZE = 30;
    static final int MAINTENANCE_FEED_SIZE = 20;

    private final DashboardDao dashboardDao;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public DashboardController(DashboardDao dashboardDao) {
        this.dashboardDao = dashboardDao;
    }

    /** 초기 화면. 카드·피드·거부 집계를 DB에서 읽어 모델에 담는다. 이후 갱신은 /stream이 맡는다. */
    @GetMapping("/")
    public String dashboard(Model model) {
        var cards = dashboardDao.stationCards(CHART_POINTS);
        model.addAttribute("stations", cards);
        model.addAttribute("stationsJson", toInlineJson(cards));
        model.addAttribute("alerts", dashboardDao.recentAlerts(ALERT_FEED_SIZE));
        model.addAttribute("maintenance", dashboardDao.recentMaintenance(MAINTENANCE_FEED_SIZE));
        model.addAttribute("rejectCounts", dashboardDao.rejectCountsByReason());
        model.addAttribute("pendingSlack", dashboardDao.pendingSlackCount());
        model.addAttribute("alertTotal", cards.stream().mapToLong(StationCard::alertCount).sum());
        model.addAttribute("ruleSummary", ruleSummary());
        model.addAttribute("chartPoints", CHART_POINTS);
        return "dashboard";
    }

    /** 헤더에 보이는 기준 요약. 예: 1=초미세먼지 경보 PM2.5≥150 · 2=… */
    private static String ruleSummary() {
        return AlertRule.BY_SEVERITY.stream()
                .map(rule -> "%d=%s %s≥%s".formatted(rule.grade(), rule.label(), rule.pollutant().label(), formatThreshold(rule.threshold())))
                .collect(Collectors.joining(" · "));
    }

    /** 정수 기준은 소수점 없이, 오존 0.12는 그대로. */
    private static String formatThreshold(double threshold) {
        return threshold == Math.rint(threshold) ? String.valueOf((long) threshold) : String.valueOf(threshold);
    }

    /** script 태그 안에 넣으므로 '<'를 이스케이프해 태그 닫힘으로 해석되지 않게 한다. */
    private String toInlineJson(List<?> value) {
        return mapper.writeValueAsString(value).replace("<", "\\u003c");
    }
}
