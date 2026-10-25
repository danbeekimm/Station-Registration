package com.exem.dust.tcp;

import com.exem.dust.common.protocol.Protocol;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * 개행 단위로 읽되 한 줄이 상한을 넘으면 그 줄의 나머지를 버린다.
 * BufferedReader.readLine()은 줄 길이에 제한이 없어 악의적인 긴 입력이 메모리를 소모할 수 있기 때문이다.
 */
final class BoundedLineReader implements Closeable {

    private final InputStream input;
    private final int maxBytes;

    BoundedLineReader(InputStream input, int maxBytes) {
        this.input = new BufferedInputStream(input);
        this.maxBytes = maxBytes;
    }

    LineRead readLine() throws IOException {
        var buffer = new ByteArrayOutputStream(256);
        boolean overflow = false;
        while (true) {
            int next = input.read();
            if (next == -1) {
                if (overflow) {
                    return new LineRead.TooLong();
                }
                return buffer.size() == 0 ? new LineRead.EndOfStream() : new LineRead.Line(buffer.toString(Protocol.CHARSET));
            }
            if (next == '\n') {
                return overflow ? new LineRead.TooLong() : new LineRead.Line(buffer.toString(Protocol.CHARSET));
            }
            if (next == '\r' || overflow) {
                continue;
            }
            if (buffer.size() >= maxBytes) {
                overflow = true;
                buffer.reset();
                continue;
            }
            buffer.write(next);
        }
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
