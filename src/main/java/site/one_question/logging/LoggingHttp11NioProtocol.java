package site.one_question.logging;

import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.NioChannel;

/**
 * Tomcat 기본 {@code Http11NioProtocol} 과 동일하되, 내부 엔드포인트만
 * {@link LoggingNioEndpoint} 로 교체한 HTTP/1.1 NIO 프로토콜.
 *
 * <p>Spring Boot 의 {@code TomcatServletWebServerFactory#setProtocol(String)} 으로
 * 이 클래스를 지정하면 커넥터가 이 프로토콜을 사용한다.
 */
public class LoggingHttp11NioProtocol extends AbstractHttp11JsseProtocol<NioChannel> {

    private static final Log log = LogFactory.getLog(LoggingHttp11NioProtocol.class);

    public LoggingHttp11NioProtocol() {
        super(new LoggingNioEndpoint());
    }

    @Override
    protected Log getLog() {
        return log;
    }

    @Override
    protected String getNamePrefix() {
        if (isSSLEnabled()) {
            return "https-" + getSslImplementationShortName() + "-nio";
        }
        return "http-nio";
    }
}
