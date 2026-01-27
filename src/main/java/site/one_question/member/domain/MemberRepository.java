package site.one_question.member.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByProviderAndProviderId(AuthSocialProvider provider, String providerId);

    Optional<Member> findByEmail(String email);
}
