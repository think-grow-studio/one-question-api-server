package site.one_question.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.one_question.logging.LoggingHttp11NioProtocol;

/**
 * 내장 Tomcat 커넥터가 TCP 연결 수립/종료를 로깅하는 커스텀 프로토콜을 사용하도록 설정한다.
 *
 * <p>실제 로그는 {@code site.one_question} 패키지가 DEBUG 인 환경(dev/local)에서만 출력된다.
 * prod 에서도 연결 로그가 필요하면 logback-spring.xml 의 prod 프로파일에
 * {@code <logger name="site.one_question.logging.LoggingNioEndpoint" level="DEBUG"/>} 를 추가하면 된다.
 */
@Configuration
public class TomcatConnectionLoggingConfig {

  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tcpConnectionLoggingCustomizer() {
    return factory -> factory.setProtocol(LoggingHttp11NioProtocol.class.getName());
  }
}
