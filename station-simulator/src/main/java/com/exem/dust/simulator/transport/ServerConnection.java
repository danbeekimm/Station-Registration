package com.exem.dust.simulator.transport;

import com.exem.dust.common.codec.NdjsonCodec;
import com.exem.dust.common.codec.dto.DecodedMessage;
import com.exem.dust.common.codec.dto.InvalidMessage;
import com.exem.dust.common.protocol.Message;
import com.exem.dust.common.protocol.Protocol;
import com.exem.dust.simulator.cli.ServerEndpoint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.time.Duration;

/** 중앙 서버와의 TCP 접속 하나. 메시지를 NDJSON 한 줄로 쓰고 읽는다. */
final class ServerConnection implements AutoCloseable {

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final NdjsonCodec codec = new NdjsonCodec();

    private ServerConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), Protocol.CHARSET));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), Protocol.CHARSET));
    }

    /** readTimeout은 ACK 대기 상한이다. 넘으면 {@link java.net.SocketTimeoutException}이 나고 호출자가 재전송을 결정한다. */
    static ServerConnection open(ServerEndpoint endpoint, Duration connectTimeout, Duration readTimeout) throws IOException {
        var socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), (int) connectTimeout.toMillis());
            socket.setSoTimeout((int) readTimeout.toMillis());
            socket.setTcpNoDelay(true);
            return new ServerConnection(socket);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /** 메시지 한 건을 한 줄로 쓰고 바로 flush한다. */
    void send(Message message) throws IOException {
        writer.write(codec.encode(message));
        writer.write('\n');
        writer.flush();
    }

    /** 서버 응답 한 줄을 읽어 메시지로 바꾼다. 연결이 닫혔거나 해석할 수 없으면 IOException으로 재접속을 유도한다. */
    Message receive() throws IOException {
        var line = reader.readLine();
        if (line == null) {
            throw new EOFException("서버가 연결을 닫았습니다");
        }
        return switch (codec.decode(line)) {
            case DecodedMessage decoded -> decoded.message();
            case InvalidMessage invalid -> throw new ProtocolException("서버 응답을 해석할 수 없습니다: " + invalid.reason());
        };
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
