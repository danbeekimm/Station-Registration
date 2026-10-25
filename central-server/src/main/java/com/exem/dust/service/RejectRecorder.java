package com.exem.dust.service;

import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.dao.RejectDao;
import com.exem.dust.domain.reject.ConnectionReject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * 거부를 connection_reject_log에 남긴다. 기록 실패가 수신 경로를 막으면 안 되므로 예외는 로그로만 처리한다.
 * 토큰이 들어 있을 수 있는 HELLO 원문은 저장하지 않고, 호출자가 넘긴 가린 표현만 저장한다.
 */
@Service
public class RejectRecorder {

    private static final Logger log = LoggerFactory.getLogger(RejectRecorder.class);

    private final RejectDao rejectDao;

    public RejectRecorder(RejectDao rejectDao) {
        this.rejectDao = rejectDao;
    }

    public void record(String remoteIp, String stationId, RejectReason reason, String rawMessage) {
        try {
            rejectDao.insert(ConnectionReject.now(remoteIp, stationId, reason, rawMessage));
        } catch (DataAccessException e) {
            log.error("거부 기록 실패 ({} {} {}): {}", remoteIp, stationId, reason, e.getMostSpecificCause().getMessage());
        }
    }
}
