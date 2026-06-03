package site.one_question.logging;

import org.apache.tomcat.util.net.NioEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP 연결 수립/종료 시점에 현재 동시 연결 수를 함께 로깅하는 NioEndpoint.
 *
 * <p>Tomcat은 소켓을 accept 하면 {@link #countUpOrAwaitConnection()}, 연결을 닫으면
 * {@link #countDownConnection()} 을 호출한다. 이 두 지점이 곧 TCP 연결의 수립/종료 시점이며,
 * 현재 동시 연결 수는 {@link #getConnectionCount()} 로 읽는다.
 *
 * <p>로그 레벨은 DEBUG. 프로젝트 logback 설정상 dev/local 에서는
 * {@code site.one_question} 패키지가 DEBUG 이므로 자동으로 노출되고, prod(INFO)에서는 조용하다.
 */
public class LoggingNioEndpoint extends NioEndpoint {

    private static final Logger log = LoggerFactory.getLogger(LoggingNioEndpoint.class);

    @Override
    protected void countUpOrAwaitConnection() throws InterruptedException {
        super.countUpOrAwaitConnection();
        if (log.isDebugEnabled()) {
            log.debug("[TCP] 연결 수립 - 현재 동시 연결 수: {}", getConnectionCount());
        }
    }

    @Override
    protected long countDownConnection() {
        long count = super.countDownConnection();
        if (log.isDebugEnabled()) {
            log.debug("[TCP] 연결 종료 - 현재 동시 연결 수: {}", getConnectionCount());
        }
        return count;
    }
}
