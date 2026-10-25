package com.exem.dust.tcp;

import com.exem.dust.common.codec.NdjsonCodec;
import com.exem.dust.common.protocol.Message;
import com.exem.dust.common.protocol.Protocol;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/** 메시지를 NDJSON 한 줄로 내보낸다. 세션 교체 시 다른 스레드가 쓸 수 있어 동기화한다. */
final class MessageWriter implements Closeable {

    private final BufferedWriter output;
    private final NdjsonCodec codec;

    MessageWriter(OutputStream output, NdjsonCodec codec) {
        this.output = new BufferedWriter(new OutputStreamWriter(output, Protocol.CHARSET));
        this.codec = codec;
    }

    /** 한 줄로 쓰고 바로 flush한다. 측정소가 이 응답을 기다리고 있다. */
    synchronized void send(Message message) throws IOException {
        output.write(codec.encode(message));
        output.write('\n');
        output.flush();
    }

    @Override
    public void close() throws IOException {
        output.close();
    }
}
