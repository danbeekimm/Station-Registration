package com.exem.dust.tcp;

import com.exem.dust.config.DustProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * java.net.ServerSocket 기반 수신 서버. accept 루프 하나가 접속을 받고, 접속마다 가상 스레드 하나가 끝까지 처리한다.
 * 블로킹 I/O를 그대로 쓰지만 가상 스레드라 접속이 늘어도 OS 스레드는 늘지 않는다.
 * 스키마 초기화와 측정소 시드가 끝난 뒤(모든 싱글턴 초기화 후) Lifecycle 단계에서 포트를 연다.
 * stop() 뒤 start()가 다시 올 수 있으므로(컨텍스트 일시 정지·재시작) 소켓과 executor는 start()마다 새로 만든다.
 */
@Component
public class TcpServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    private final DustProperties.Tcp tcp;
    private final ConnectionHandlerFactory handlerFactory;
    private final SessionRegistry sessions;

    private ServerSocket serverSocket;
    private ExecutorService connectionExecutor;
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
        connectionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        running = true;
        acceptorThread = Thread.ofPlatform().name("tcp-acceptor").start(this::acceptLoop);
        log.info("TCP 수신 시작: 포트 {}", boundPort());
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
            } catch (SocketException e) {
                if (running) {
                    log.warn("accept 실패: {}", e.getMessage());
                }
                continue;
            } catch (IOException e) {
                log.warn("accept 실패: {}", e.getMessage());
                continue;
            }
            try {
                connectionExecutor.execute(handlerFactory.create(socket));
            } catch (RejectedExecutionException e) {
                // 종료 중에 들어온 접속. accept 루프를 죽이지 말고 그 접속만 닫는다
                log.warn("종료 중이라 접속을 받지 않음: {}", socket.getRemoteSocketAddress());
                closeQuietly(socket);
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        closeQuietly(serverSocket);
        sessions.closeAll();
        if (connectionExecutor != null) {
            connectionExecutor.shutdownNow();
        }
        if (acceptorThread != null) {
            acceptorThread.interrupt();
        }
        log.info("TCP 수신 종료");
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            log.debug("소켓 종료 중 예외", e);
        }
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
