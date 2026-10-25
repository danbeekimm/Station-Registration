package com.exem.dust.common.codec;

import com.exem.dust.common.protocol.Message;
import com.exem.dust.common.protocol.RejectReason;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 메시지 ↔ JSON 한 줄 변환. 개행이 메시지 경계이므로 출력에는 줄바꿈이 들어가지 않는다
 * (Jackson이 문자열 안의 제어 문자를 이스케이프한다).
 */
public final class NdjsonCodec {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public String encode(Message message) {
        return mapper.writeValueAsString(message);
    }

    /** 구문 오류는 MALFORMED, JSON은 맞지만 메시지 형태가 아니면 SCHEMA. 클라이언트에 내부 예외 문구는 돌려주지 않는다. */
    public DecodeResult decode(String line) {
        try {
            return new DecodeResult.Decoded(mapper.readValue(line, Message.class));
        } catch (StreamReadException e) {
            return new DecodeResult.Invalid(RejectReason.MALFORMED, "JSON 구문 오류");
        } catch (DatabindException e) {
            return new DecodeResult.Invalid(RejectReason.SCHEMA, "메시지 형식 불일치");
        } catch (JacksonException e) {
            return new DecodeResult.Invalid(RejectReason.MALFORMED, "메시지를 해석할 수 없음");
        }
    }
}
