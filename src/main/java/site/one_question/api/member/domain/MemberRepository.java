package site.one_question.api.member.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByProviderAndProviderId(AuthSocialProvider provider, String providerId);

    Optional<Member> findByProviderAndProviderId(AuthSocialProvider provider, String providerId);

    Optional<Member> findByEmail(String email);
}
