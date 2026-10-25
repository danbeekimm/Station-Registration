package com.exem.dust.tcp;

import com.exem.dust.config.DustProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * java.net.ServerSocket 기반 수신 서버. accept 루프 하나가 접속을 받고, 접속마다 가상 스레드 하나가 끝까지 처리한다.
 * 블로킹 I/O를 그대로 쓰지만 가상 스레드라 접속이 늘어도 OS 스레드는 늘지 않는다.
 * 스키마 초기화와 측정소 시드가 끝난 뒤(모든 싱글턴 초기화 후) Lifecycle 단계에서 포트를 연다.
 */
@Component
public class TcpServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    private final DustProperties.Tcp tcp;
    private final ConnectionHandlerFactory handlerFactory;
    private final SessionRegistry sessions;
    private final ExecutorService connectionExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private ServerSocket serverSocket;
    private Thread acceptorThread;
    private volatile boolean running;

    public TcpServer(DustProperties properties, ConnectionHandlerFactory handlerFactory, SessionRegistry sessions) {
        this.tcp = properties.tcp();
        this.handlerFactory = handlerFactory;
        this.sessions = sessions;
    }

    @Override
    public void start() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(tcp.port()));
        } catch (IOException e) {
            throw new IllegalStateException("TCP 포트 %d를 열 수 없습니다".formatted(tcp.port()), e);
        }
        running = true;
        acceptorThread = Thread.ofPlatform().name("tcp-acceptor").start(this::acceptLoop);
        log.info("TCP 수신 시작: 포트 {}", boundPort());
    }

    private void acceptLoop() {
        while (running) {
            try {
                var socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                connectionExecutor.execute(handlerFactory.create(socket));
            } catch (SocketException e) {
                if (running) {
                    log.warn("accept 실패: {}", e.getMessage());
                }
            } catch (IOException e) {
                log.warn("accept 실패: {}", e.getMessage());
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.debug("서버 소켓 종료 중 예외", e);
        }
        sessions.closeAll();
        connectionExecutor.shutdownNow();
        if (acceptorThread != null) {
            acceptorThread.interrupt();
        }
        log.info("TCP 수신 종료");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 실제로 열린 포트. 설정이 0이면 임시 포트가 배정되므로 테스트는 이 값을 쓴다. */
    public int boundPort() {
        return serverSocket.getLocalPort();
    }
}
