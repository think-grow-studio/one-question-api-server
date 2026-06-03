package site.one_question.logging;

import java.io.CharArrayWriter;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.AbstractAccessLogValve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 응답이 완전히 끝난 뒤 전송된 바이트 수(=gzip 압축 후 크기)와 원본 바이트 수를 로깅하는 밸브.
 *
 * <p>압축은 서블릿 레이어보다 아래(Tomcat 출력단)에서 일어나므로 일반 필터에서는
 * 압축 전 크기만 보인다. 반면 {@link AbstractAccessLogValve#log(Request, Response, long)} 은
 * 커넥터가 {@code response.finishResponse()} 이후에 호출하므로, 이 시점의
 * {@link Response#getBytesWritten(boolean)} 이 실제 전송된(압축된) 크기다.
 *
 * <ul>
 *   <li>{@link Response#getContentWritten()} : 애플리케이션이 쓴 원본 바이트(압축 전)</li>
 *   <li>{@link Response#getBytesWritten(boolean)} : 소켓으로 전송된 바이트(압축 후)</li>
 * </ul>
 *
 * <p>DEBUG 레벨이라 dev/local 에서만 노출된다.
 */
public class ResponseSizeLoggingValve extends AbstractAccessLogValve {

  private static final Logger log = LoggerFactory.getLogger(ResponseSizeLoggingValve.class);

  public ResponseSizeLoggingValve() {
    // 패턴은 사용하지 않지만 라이프사이클 시작 시 파싱되므로 안전한 값을 지정한다.
    setPattern("%b");
  }

  @Override
  public void log(Request request, Response response, long time) {
    if (!log.isDebugEnabled()) {
      return;
    }
    long original = response.getContentWritten();
    long sent = response.getBytesWritten(false);
    String encoding = response.getHeader("Content-Encoding");
    boolean compressed = encoding != null && encoding.contains("gzip");

    if (compressed && original > 0) {
      double ratio = 100.0 * (original - sent) / original;
      log.debug(
          "[RESP] {} {} status={} 원본={}B 전송={}B(gzip) 절감={}%",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          original,
          sent,
          String.format("%.1f", ratio));
    } else {
      log.debug(
          "[RESP] {} {} status={} 전송={}B(미압축)",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          sent);
    }
  }

  /** 패턴 기반 출력은 사용하지 않으므로 비워둔다. */
  @Override
  protected void log(CharArrayWriter message) {
    // no-op: log(Request, Response, long) 에서 직접 처리
  }
}
