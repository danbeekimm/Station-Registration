package com.exem.dust.tcp;

import com.exem.dust.common.codec.NdjsonCodec;
import com.exem.dust.config.DustProperties;
import com.exem.dust.service.IngestService;
import com.exem.dust.service.MessageValidator;
import com.exem.dust.service.StationAuthService;
import com.exem.dust.service.StationStateLoader;
import org.springframework.stereotype.Component;

import java.net.Socket;

/** 접속마다 새 {@link ConnectionHandler}를 만든다. 핸들러는 빈이 아니라 접속 수명에 묶인 객체다. */
@Component
public class ConnectionHandlerFactory {

    private final DustProperties properties;
    private final NdjsonCodec codec = new NdjsonCodec();
    private final StationAuthService authService;
    private final StationStateLoader stateLoader;
    private final MessageValidator validator;
    private final IngestService ingestService;
    private final SessionRegistry sessions;

    public ConnectionHandlerFactory(DustProperties properties, StationAuthService authService, StationStateLoader stateLoader,
                                    MessageValidator validator, IngestService ingestService, SessionRegistry sessions) {
        this.properties = properties;
        this.authService = authService;
        this.stateLoader = stateLoader;
        this.validator = validator;
        this.ingestService = ingestService;
        this.sessions = sessions;
    }

    ConnectionHandler create(Socket socket) {
        return new ConnectionHandler(socket, properties.tcp(), codec, authService, stateLoader, validator, ingestService, sessions);
    }
}
