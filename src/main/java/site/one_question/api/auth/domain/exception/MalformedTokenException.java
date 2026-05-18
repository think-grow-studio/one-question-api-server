package site.one_question.api.auth.domain.exception;

public class MalformedTokenException extends AuthException {

    public MalformedTokenException() {
        super(AuthExceptionSpec.MALFORMED_TOKEN);
    }
}
