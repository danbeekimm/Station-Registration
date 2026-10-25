package com.exem.dust.support;

import com.exem.dust.common.codec.DecodeResult;
import com.exem.dust.common.codec.NdjsonCodec;
import com.exem.dust.common.protocol.Hello;
import com.exem.dust.common.protocol.Message;
import com.exem.dust.common.protocol.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

/** 테스트가 측정소 역할로 서버에 접속할 때 쓰는 최소 클라이언트. 원문 줄도 보낼 수 있어 검증 실패 시나리오에 쓴다. */
public final class TestStationClient implements AutoCloseable {

    public static final String NOWON_ID = "111311";
    public static final String NOWON_TOKEN = "dev-token-nowon-111311";

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final NdjsonCodec codec = new NdjsonCodec();

    public TestStationClient(int port) throws IOException {
        socket = new Socket("localhost", port);
        socket.setSoTimeout(5_000);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), Protocol.CHARSET));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), Protocol.CHARSET));
    }

    public Message hello(String stationId, String token) throws IOException {
        return request(new Hello(stationId, stationId, token, Protocol.VERSION, 0));
    }

    public Message request(Message message) throws IOException {
        send(message);
        return receive();
    }

    public void send(Message message) throws IOException {
        sendRaw(codec.encode(message));
    }

    public void sendRaw(String line) throws IOException {
        writer.write(line);
        writer.write('\n');
        writer.flush();
    }

    public Message receive() throws IOException {
        var line = reader.readLine();
        if (line == null) {
            throw new IOException("서버가 연결을 닫음");
        }
        return switch (codec.decode(line)) {
            case DecodeResult.Decoded(Message message) -> message;
            case DecodeResult.Invalid invalid -> throw new IOException("해석 불가 응답: " + invalid);
        };
    }

    /** 서버가 소켓을 닫았으면 true. 읽기 타임아웃(5초) 안에 EOF가 와야 한다. */
    public boolean isClosedByServer() throws IOException {
        return reader.readLine() == null;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
